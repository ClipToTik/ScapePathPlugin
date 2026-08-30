/*
 * Copyright (c) 2026, ScapePath
 * All rights reserved. BSD 2-Clause. See LICENSE file.
 */
package com.scapepath.plugin.transport;

/**
 * A minimal, deterministic JSON writer with no reflection and no external dependencies.
 *
 * <p>Field/element ordering is entirely controlled by the caller, so the same sequence of
 * calls always produces byte-for-byte identical output. This is deliberately hand-written
 * rather than using a reflective library (e.g. Gson) so that the transport contract is
 * explicit, deterministic, and free of Java class metadata &mdash; and so the plugin keeps
 * its no-reflection guarantee.</p>
 *
 * <p>Not general-purpose: it trusts the caller to balance objects/arrays. It emits compact
 * JSON (no whitespace).</p>
 */
public final class JsonWriter
{
	private final StringBuilder sb = new StringBuilder(256);
	// Tracks whether the current container already has a member, to place commas.
	private boolean needComma = false;

	public JsonWriter beginObject()
	{
		preValue();
		sb.append('{');
		needComma = false;
		return this;
	}

	public JsonWriter endObject()
	{
		sb.append('}');
		needComma = true;
		return this;
	}

	public JsonWriter beginArray()
	{
		preValue();
		sb.append('[');
		needComma = false;
		return this;
	}

	public JsonWriter endArray()
	{
		sb.append(']');
		needComma = true;
		return this;
	}

	/** Write an object key. Must be followed by exactly one value. */
	public JsonWriter name(String key)
	{
		if (needComma)
		{
			sb.append(',');
		}
		writeString(key);
		sb.append(':');
		needComma = false;
		return this;
	}

	public JsonWriter value(String v)
	{
		preValue();
		if (v == null)
		{
			sb.append("null");
		}
		else
		{
			writeString(v);
		}
		needComma = true;
		return this;
	}

	public JsonWriter value(long v)
	{
		preValue();
		sb.append(v);
		needComma = true;
		return this;
	}

	public JsonWriter value(int v)
	{
		preValue();
		sb.append(v);
		needComma = true;
		return this;
	}

	public JsonWriter value(boolean v)
	{
		preValue();
		sb.append(v ? "true" : "false");
		needComma = true;
		return this;
	}

	/** Explicit JSON null (used for a nullable numeric or an absent section payload). */
	public JsonWriter nullValue()
	{
		preValue();
		sb.append("null");
		needComma = true;
		return this;
	}

	/** Nullable boxed long: writes the number or JSON null. */
	public JsonWriter value(Long v)
	{
		return v == null ? nullValue() : value(v.longValue());
	}

	public String toJson()
	{
		return sb.toString();
	}

	private void preValue()
	{
		// Insert a comma between array elements (name() handles object members).
		if (needComma)
		{
			sb.append(',');
		}
	}

	private void writeString(String s)
	{
		sb.append('"');
		for (int i = 0; i < s.length(); i++)
		{
			final char c = s.charAt(i);
			switch (c)
			{
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\f':
					sb.append("\\f");
					break;
				default:
					if (c < 0x20)
					{
						sb.append(String.format("\\u%04x", (int) c));
					}
					else
					{
						sb.append(c);
					}
					break;
			}
		}
		sb.append('"');
	}
}
