/*
 * MIT License
 *
 * Copyright (c) 2021 alvin0319
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package me.alvin0319.neisapi.school

import com.fasterxml.jackson.module.kotlin.readValue
import me.alvin0319.neisapi.request.Request
import me.alvin0319.neisapi.request.Request.mapper
import me.alvin0319.neisapi.school.meal.Meal
import me.alvin0319.neisapi.types.SchoolDistrictList
import me.alvin0319.neisapi.util.meal.SchoolMealSearchResult

internal typealias YearInt = Int
internal typealias MonthInt = Int
internal typealias DateInt = Int

object SchoolMeal {
    private val day: List<String> = listOf(
        "sun",
        "mon",
        "tue",
        "wed",
        "the",
        "fri",
        "sat"
    )
    private val result: MutableMap<SchoolDistrictList, MutableMap<String, MutableMap<YearInt, MutableMap<MonthInt, MutableMap<DateInt, Meal>>>>> =
        mutableMapOf()

    @JvmOverloads
    @JvmStatic
    fun getMeal(school: School, year: Int, month: Int, refresh: Boolean = false): MutableMap<DateInt, Meal> {
        val edu = school.edu
        if (
            !refresh && result.getOrPut(edu) { mutableMapOf() }.getOrPut(school.code) { mutableMapOf() }
                .getOrPut(year) { mutableMapOf() }.containsKey(month)
        ) {
            return result.getValue(edu).getValue(school.code).getValue(year).getValue(month)
        }
        val response = Request.createRequest(
            "sts_sci_md00_001.ws", edu,
            mapOf(
                "ay" to year.toString(),
                "mm" to ((if (month < 10) "0" else "") + month),
                "schulCode" to school.code,
                "schulCrseScCode" to school.kind.id.toString()
            )
        )

        val mealSearchResult = mapper.readValue<SchoolMealSearchResult>(
            response.entity.content
        )
        mealSearchResult.resultSVO?.mthDietList?.let { lists ->
            lists.forEach mthDiet@{
                day.forEach dayF@{ d ->
                    val monthStr = when (d) {
                        "sun" -> it.sun ?: ""
                        "mon" -> it.mon ?: ""
                        "tue" -> it.tue ?: ""
                        "wed" -> it.wed ?: ""
                        "the" -> it.the ?: ""
                        "fri" -> it.fri ?: ""
                        "sat" -> it.sat ?: ""
                        else -> it.mon ?: ""
                    }
                    val dateStr = if (monthStr.length >= 2) {
                        monthStr.split("<br />").toMutableList().removeFirst().toInt()
                    } else {
                        -1
                    }
                    if (dateStr == -1) {
                        return@dayF
                    }
                    if (it.weekGb?.toInt() == 0 || it.weekGb?.toInt() == lists.size + 1) {
                        if (it.weekGb.toInt() == 0) {
                            if (dateStr > 7) {
                                return@dayF
                            }
                        } else if (it.weekGb.toInt() == lists.size + 1) {
                            if (dateStr < 10) {
                                return@dayF
                            }
                        }
                    }
                    val meal = Meal.parseMonth(
                        monthStr,
                        year, month
                    )
                    result.getOrPut(edu) {
                        mutableMapOf()
                    }[school.code]!!.getOrPut(year) { mutableMapOf() }
                        .getOrPut(month) { mutableMapOf() }[meal.date.date] = meal
                }
            }

            return result[edu]!![school.code]!![year]!![month]!!
        }
        throw AssertionError("Invalid data received")
    }
}
