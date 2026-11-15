package com.sujoy.nextset.data

import com.sujoy.nextset.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned explicit format, also used for immutable session snapshots. No reflection. */
object TemplateCodec {
    fun encode(template: WorkoutTemplate): String =
        JSONObject()
            .apply {
                put("version", 1)
                put("id", template.id)
                put("name", template.name)
                put(
                    "blocks",
                    JSONArray().apply {
                        template.blocks.forEach { block ->
                            put(
                                JSONObject().apply {
                                    put("id", block.id)
                                    put("superset", block.superset)
                                    put("betweenSets", block.betweenSets)
                                    put("betweenMovements", block.betweenMovements)
                                    put("betweenRounds", block.betweenRounds)
                                    put("afterBlock", block.afterBlock)
                                    put(
                                        "exercises",
                                        JSONArray().apply {
                                            block.exercises.forEach { e ->
                                                put(
                                                    JSONObject().apply {
                                                        put("id", e.id)
                                                        put("name", e.name)
                                                        put(
                                                            "rests",
                                                            JSONArray().apply {
                                                                e.rests.forEach {
                                                                    put(it ?: JSONObject.NULL)
                                                                }
                                                            },
                                                        )
                                                    }
                                                )
                                            }
                                        },
                                    )
                                }
                            )
                        }
                    },
                )
            }
            .toString()

    fun decode(value: String, validate: Boolean = true): WorkoutTemplate {
        val root = JSONObject(value)
        require(root.getInt("version") == 1) { "Unsupported workout snapshot version" }
        return WorkoutTemplate(
                root.getString("id"),
                root.getString("name"),
                root.getJSONArray("blocks").objects().map { b ->
                    WorkoutBlock(
                        b.getString("id"),
                        b.getJSONArray("exercises").objects().map { e ->
                            val rests = e.getJSONArray("rests")
                            Exercise(
                                e.getString("id"),
                                e.getString("name"),
                                (0 until rests.length()).map {
                                    if (rests.isNull(it)) null else rests.getInt(it)
                                },
                            )
                        },
                        b.getBoolean("superset"),
                        b.getInt("betweenSets"),
                        b.getInt("betweenMovements"),
                        b.getInt("betweenRounds"),
                        b.getInt("afterBlock"),
                    )
                },
            )
            .also {
                if (validate) require(it.validationError() == null) { "Invalid stored workout" }
            }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
