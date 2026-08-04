package com.anfas.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform