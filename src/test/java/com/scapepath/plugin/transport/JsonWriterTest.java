/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class JsonWriterTest
{
	@Test
	public void writesObjectWithMixedTypes()
	{
		String json = new JsonWriter().beginObject()
			.name("s").value("hi")
			.name("i").value(5)
			.name("l").value(9_000_000_000L)
			.name("b").value(true)
			.name("n").nullValue()
			.endObject().toJson();

		assertEquals("{\"s\":\"hi\",\"i\":5,\"l\":9000000000,\"b\":true,\"n\":null}", json);
	}

	@Test
	public void escapesSpecialCharacters()
	{
		String json = new JsonWriter().beginObject()
			.name("q").value("a\"b\\c\nd\te")
			.endObject().toJson();
		// Valid JSON that round-trips to the original string.
		JsonObject o = new JsonParser().parse(json).getAsJsonObject();
		assertEquals("a\"b\\c\nd\te", o.get("q").getAsString());
	}

	@Test
	public void emptyArrayAndNestedObjects()
	{
		String json = new JsonWriter().beginObject()
			.name("arr").beginArray().endArray()
			.name("obj").beginObject().name("x").value(1).endObject()
			.endObject().toJson();
		assertEquals("{\"arr\":[],\"obj\":{\"x\":1}}", json);
	}

	@Test
	public void nullableBoxedLong()
	{
		Long present = 42L;
		Long absent = null;
		String json = new JsonWriter().beginObject()
			.name("p").value(present)
			.name("a").value(absent)
			.endObject().toJson();
		assertTrue(json.contains("\"p\":42"));
		assertTrue(json.contains("\"a\":null"));
	}
}
