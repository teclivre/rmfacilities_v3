package com.rmfacilities.app.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun LocalDate.toBrDate(): String = format(dateFormatter)
fun LocalDateTime.toBrDateTime(): String = format(dateTimeFormatter)
