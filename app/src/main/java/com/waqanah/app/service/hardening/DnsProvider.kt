package com.waqanah.app.service.hardening

enum class DnsProvider(val host: String) {
    FAMILY("family-filter-dns.cleanbrowsing.org"),
    ADULT("adult-filter-dns.cleanbrowsing.org"),
    CUSTOM("")
}
