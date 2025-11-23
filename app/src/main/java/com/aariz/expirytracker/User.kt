package com.aariz.expirytracker

data class User(
    var id: String = "",
    var firstName: String = "",
    var lastName: String = "",
    var email: String = "",
    var dateOfBirth: String = "",
    var createdAt: Long = 0
)