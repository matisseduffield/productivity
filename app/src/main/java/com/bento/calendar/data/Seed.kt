package com.bento.calendar.data

import java.util.UUID

fun newId(): String = "x" + UUID.randomUUID().toString().replace("-", "").take(12)
