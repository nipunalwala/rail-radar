package com.trainnearme.core.domain

/** A delay as hours and minutes: 12 becomes "0:12" and 81 becomes "1:21". */
fun formatDelay(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
