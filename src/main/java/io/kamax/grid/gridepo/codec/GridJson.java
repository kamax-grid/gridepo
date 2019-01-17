/*
 * Gridepo - Grid Data Server
 * Copyright (C) 2019 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.grid.gridepo.codec;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;

public class GridJson {

    // Needed to avoid silly try/catch block in various places
    // We only use ByteArray streams, so IOException will not happen (unless irrecoverable situation like OOM)
    private static class JsonWriterUnchecked extends JsonWriter {

        public JsonWriterUnchecked(Writer out) {
            super(out);
        }

        @Override
        public JsonWriter name(String value) {
            try {
                return super.name(value);
            } catch (IOException e) {
                throw new JsonIOException(e);
            }
        }
    }

    private static void encodeCanonical(JsonObject el, JsonWriterUnchecked writer) throws IOException {
        writer.beginObject();
        el.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEachOrdered(entry -> {
            writer.name(entry.getKey());
            encodeCanonicalElement(entry.getValue(), writer);
        });
        writer.endObject();
    }

    private static void encodeCanonicalArray(JsonArray array, JsonWriterUnchecked writer) throws IOException {
        writer.beginArray();
        array.forEach(el -> encodeCanonicalElement(el, writer));
        writer.endArray();
    }

    private static void encodeCanonicalElement(JsonElement el, JsonWriterUnchecked writer) {
        try {
            if (el.isJsonObject()) encodeCanonical(el.getAsJsonObject(), writer);
            else if (el.isJsonPrimitive()) writer.jsonValue(el.toString());
            else if (el.isJsonArray()) encodeCanonicalArray(el.getAsJsonArray(), writer);
            else if (el.isJsonNull()) writer.nullValue();
            else
                throw new RuntimeException("Unexpected JSON type in GridJson canonical methods, this is a bug, report!");
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }

    public static String encodeCanonical(JsonObject obj) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JsonWriterUnchecked writer = new JsonWriterUnchecked(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.setIndent("");
            writer.setHtmlSafe(false);
            writer.setLenient(false);

            encodeCanonical(obj, writer);
            writer.close();
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }

}
