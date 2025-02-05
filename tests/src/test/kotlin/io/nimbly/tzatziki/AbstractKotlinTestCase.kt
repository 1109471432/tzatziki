/*
 * CUCUMBER +
 * Copyright (C) 2023  Maxime HAMM - NIMBLY CONSULTING - maxime.hamm.pro@gmail.com
 *
 * This document is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.nimbly.tzatziki

import org.junit.Ignore

@Ignore
abstract class AbstractKotlinTestCase : AbstractTestCase() {

    fun configure(text: String)
        = super.configure(EXT.kt, text)

    override fun setUp() {
        super.setUp()
        setupForKotlin()
    }

    fun addClass(text: String)
        = addClass(EXT.kt, text)
}