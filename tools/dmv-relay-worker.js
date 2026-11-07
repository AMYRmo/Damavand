/**
 * Damavand - DMV Short Link Relay Worker
 *
 * راهنمای نصب و راه‌اندازی در کلودفلر (کاملاً رایگان):
 * ۱. وارد پنل Cloudflare شوید و به بخش Workers & Pages > KV بروید.
 * ۲. یک KV Namespace بسازید به نام DMV_KV.
 * ۳. یک Worker جدید بسازید (Workers & Pages > Create application > Create Worker).
 * ۴. کد زیر را کپی کرده و جایگزین محتوای Worker کنید.
 * ۵. در تنظیمات Worker (Settings > Variables > KV Namespace Bindings):
 *    یک Binding اضافه کنید:
 *    - Variable name: DMV_KV
 *    - KV namespace: DMV_KV (همانی که در مرحله ۲ ساختید)
 * ۶. (اختیاری) اگر می‌خواهید فقط خودتان بتوانید کانفیگ آپلود کنید، در همان بخش Variables یک Environment Variable به نام AUTH_TOKEN بسازید و رمز دلخواه بگذارید و همان را در برنامه وارد کنید.
 * ۷. دکمه Save and Deploy را بزنید. آدرس Worker (مثلاً https://dmv-relay.yourname.workers.dev) را در بخش «قفل و اشتراک‌گذاری» دماوند وارد کنید.
 *
 * رله فقط متن رمزشده‌ی کانفیگ (.dmv) را نگه می‌دارد و هیچ‌وقت کلید یا محتوای کانفیگ را نمی‌بیند.
 * اگر هنگام آپلود «اثبات کلید» (X-DMV-Proof) فرستاده شود، بلاگ فقط به اولین دستگاهی داده می‌شود
 * که همان کلید را ثابت کند (POST /claim) و بعد از آن برای دستگاه‌های دیگر قفل می‌شود.
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, X-DMV-Token, X-DMV-Proof, User-Agent",
        },
      });
    }

    if (!env.DMV_KV) {
      return new Response("DMV_KV binding is missing", { status: 500 });
    }

    // GET /get/:id -> دریافت کانفیگ رمزشده (فقط وقتی کلید یک‌بارمصرف ندارد)
    if (request.method === "GET" && url.pathname.startsWith("/get/")) {
      const id = url.pathname.substring("/get/".length).trim();
      if (!id || id.length < 6 || id.length > 64) {
        return new Response("شناسه نامعتبر است", { status: 400 });
      }

      const proof = await env.DMV_KV.get(id + ":proof");
      if (proof) {
        // این لینک کلید دارد؛ باید از /claim گرفته شود.
        return new Response("A private key is required for this link", { status: 401 });
      }

      const blob = await env.DMV_KV.get(id);
      if (!blob) {
        return new Response("این لینک پیدا نشد یا منقضی شده است", { status: 404 });
      }

      return new Response(blob, {
        status: 200,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Cache-Control": "public, max-age=300",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // POST /claim/:id -> دریافت با اثبات کلید؛ فقط اولین دستگاه موفق می‌شود
    if (request.method === "POST" && url.pathname.startsWith("/claim/")) {
      const id = url.pathname.substring("/claim/".length).trim();
      if (!id || id.length < 6 || id.length > 64) {
        return new Response("شناسه نامعتبر است", { status: 400 });
      }

      let payload;
      try {
        payload = await request.json();
      } catch (e) {
        return new Response("Bad request", { status: 400 });
      }
      const proof = (payload && payload.proof) || "";
      const device = (payload && payload.device) || "";

      const storedProof = await env.DMV_KV.get(id + ":proof");
      if (!storedProof) {
        return new Response("این لینک پیدا نشد یا منقضی شده است", { status: 404 });
      }
      if (!proof || proof !== storedProof) {
        return new Response("کلید نامعتبر است", { status: 403 });
      }

      const claimedBy = await env.DMV_KV.get(id + ":claim");
      if (claimedBy && claimedBy !== device) {
        return new Response("این لینک قبلاً روی دستگاه دیگری فعال شده است", { status: 409 });
      }
      if (!claimedBy && device) {
        await env.DMV_KV.put(id + ":claim", device);
      }

      const blob = await env.DMV_KV.get(id);
      if (!blob) {
        return new Response("این لینک پیدا نشد یا منقضی شده است", { status: 404 });
      }

      return new Response(blob, {
        status: 200,
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    // POST /put -> ذخیره کانفیگ رمزشده و دریافت شناسه کوتاه
    if (request.method === "POST" && url.pathname === "/put") {
      // بررسی توکن اختیاری
      if (env.AUTH_TOKEN) {
        const clientToken = request.headers.get("X-DMV-Token");
        if (!clientToken || clientToken !== env.AUTH_TOKEN) {
          return new Response("Unauthorized", { status: 401 });
        }
      }

      const body = await request.text();
      if (!body || body.length < 16 || body.length > 6_000_000) {
        return new Response("داده ارسالی نامعتبر یا بیش از حد بزرگ است", { status: 400 });
      }

      // تولید شناسه تصادفی ۱۰ کاراکتری ایمن برای URL
      const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
      const randomValues = new Uint8Array(10);
      crypto.getRandomValues(randomValues);
      let id = "";
      for (let i = 0; i < 10; i++) {
        id += chars[randomValues[i] % chars.length];
      }

      // مدت زمان اعتبار (پیش‌فرض ۳۰ روز یا بر اساس پارامتر ttl)
      const ttlParam = parseInt(url.searchParams.get("ttl") || "0", 10);
      const ttlSeconds = (ttlParam >= 60) ? Math.min(ttlParam, 365 * 24 * 3600) : 30 * 24 * 3600;

      await env.DMV_KV.put(id, body, { expirationTtl: ttlSeconds });

      // اثبات کلید یک‌بارمصرف (هش کلید، نه خود کلید) — اختیاری
      const proof = request.headers.get("X-DMV-Proof");
      if (proof) {
        await env.DMV_KV.put(id + ":proof", proof, { expirationTtl: ttlSeconds });
      }

      return new Response(JSON.stringify({ id }), {
        status: 200,
        headers: {
          "Content-Type": "application/json; charset=utf-8",
          "Access-Control-Allow-Origin": "*",
        },
      });
    }

    return new Response("Damavand DMV Relay Worker is active.", {
      status: 200,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  },
};
