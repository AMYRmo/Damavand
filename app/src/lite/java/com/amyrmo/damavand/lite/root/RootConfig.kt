package com.amyrmo.damavand.lite.root

/**
 * Root-mode constants, ported verbatim from PattNG's AppConfig ROOT_* block
 * (3.2.10). One place holds them so the iptables scripts, the HEV config and
 * the teardown all agree on names, marks and tables.
 */
object RootConfig {
    const val LOOPBACK = "127.0.0.1"
    const val RUNTIME_DIR = "sys_cache"
    const val IPTABLES_CHAIN = "CORE_FILTER"
    const val FWMARK = 255
    const val MARK_ROUTE = 1
    const val ROUTE_TABLE = 2024
    const val RULE_PRIORITY = 1000
    const val TUN_NAME = "utun7788"
    const val TUN_ADDR_V4 = "198.18.0.1/15"
    const val TUN_ADDR_V6 = "fdfe:dcba:9876::1/64"
    const val TUN2SOCKS_BIN = "libhevsockstun.so"
    const val FWD_CHAIN = "CORE_FWD"
    const val DNS_CHAIN = "CORE_DNS"
    const val DNS_OUT_CHAIN = "CORE_DNS_OUT"
    const val V6_CHAIN = "CORE6_FILTER"
    const val V6_FWD_CHAIN = "CORE6_FWD"
    const val V6_PRE_CHAIN = "CORE6_PRE"
    const val LAN_DNS = "1.1.1.1"
    const val OOM_SCORE = "-1000"
}
