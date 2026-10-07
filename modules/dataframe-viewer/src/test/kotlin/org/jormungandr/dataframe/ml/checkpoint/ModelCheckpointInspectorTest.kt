/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.dataframe.ml.checkpoint

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

class ModelCheckpointInspectorTest {

    @Test
    fun testSafetensorsHeaderParsingZeroCopy(@TempDir tempDir: Path) {
        val testFile = tempDir.resolve("model.safetensors").toFile()

        val jsonHeader = """
        {
            "__metadata__": {
                "format": "pt",
                "framework": "PyTorch 2.4",
                "quantized": "false"
            },
            "model.embed_tokens.weight": {
                "dtype": "F16",
                "shape": [32000, 4096],
                "data_offsets": [0, 262144000]
            },
            "model.layers.0.self_attn.q_proj.weight": {
                "dtype": "F16",
                "shape": [4096, 4096],
                "data_offsets": [262144000, 295698432]
            },
            "model.layers.0.mlp.gate_proj.weight": {
                "dtype": "F16",
                "shape": [11008, 4096],
                "data_offsets": [295698432, 385863680]
            },
            "model.norm.weight": {
                "dtype": "F32",
                "shape": [4096],
                "data_offsets": [385863680, 385880064]
            }
        }
        """.trimIndent()

        val headerBytes = jsonHeader.toByteArray(Charsets.UTF_8)
        val headerLength = headerBytes.size.toLong()

        FileOutputStream(testFile).use { fos ->
            val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buf.putLong(headerLength)
            fos.write(buf.array())
            fos.write(headerBytes)
            // Write a small dummy payload after header
            fos.write(ByteArray(1024))
        }

        val result = ModelCheckpointInspector.inspectFile(testFile)

        assertEquals(CheckpointFormat.SAFETENSORS, result.format)
        assertEquals(4, result.tensors.size)
        assertEquals("PyTorch 2.4", result.metadata["framework"])

        val embed = result.tensors.first { it.name == "model.embed_tokens.weight" }
        assertEquals(listOf(32000L, 4096L), embed.shape)
        assertEquals("F16", embed.dtype)
        assertEquals(131_072_000L, embed.parameterCount)
        assertEquals("Embedding Layer", embed.layerType)

        val qProj = result.tensors.first { it.name == "model.layers.0.self_attn.q_proj.weight" }
        assertEquals(listOf(4096L, 4096L), qProj.shape)
        assertEquals("Self-Attention", qProj.layerType)

        val norm = result.tensors.first { it.name == "model.norm.weight" }
        assertEquals("Layer Normalization", norm.layerType)

        assertTrue(result.totalParameters > 190_000_000L)
        assertTrue(result.architectureSummary.contains("parameters"))
        assertEquals("Hugging Face Safetensors", result.format.displayName)
    }

    @Test
    fun testFallbackAndGenericHandling(@TempDir tempDir: Path) {
        val genericFile = tempDir.resolve("custom_model.bin").toFile()
        genericFile.writeBytes(ByteArray(2048))

        val result = ModelCheckpointInspector.inspectFile(genericFile)
        assertEquals(CheckpointFormat.GENERIC_BINARY, result.format)
        assertEquals(2048L, result.fileSizeBytes)

        val onnxFile = tempDir.resolve("classifier.onnx").toFile()
        onnxFile.writeBytes(ByteArray(4096))
        val onnxResult = ModelCheckpointInspector.inspectFile(onnxFile)
        assertEquals(CheckpointFormat.ONNX, onnxResult.format)
    }
}
