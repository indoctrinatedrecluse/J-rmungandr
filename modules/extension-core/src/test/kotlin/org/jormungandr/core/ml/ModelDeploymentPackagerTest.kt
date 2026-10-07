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

package org.jormungandr.core.ml

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ModelDeploymentPackagerTest {

    @Test
    fun `test generate bundle generates complete microservice`() {
        val bundle = ModelDeploymentPackager.generateBundle(
            modelName = "House Price Estimator",
            framework = "Scikit-Learn",
            inputFeatures = listOf("sqft", "bedrooms", "bathrooms", "zipcode")
        )

        assertEquals("house_price_estimator", bundle.modelName)
        assertEquals("Scikit-Learn", bundle.framework)

        // Validate FastAPI code
        assertTrue(bundle.appPy.contains("from fastapi import FastAPI"))
        assertTrue(bundle.appPy.contains("class PredictionInput(BaseModel):"))
        assertTrue(bundle.appPy.contains("sqft: float"))
        assertTrue(bundle.appPy.contains("bedrooms: float"))
        assertTrue(bundle.appPy.contains("@app.get(\"/health\""))
        assertTrue(bundle.appPy.contains("@app.post(\"/predict\""))

        // Validate Dockerfile
        assertTrue(bundle.dockerfile.contains("FROM python:3.11-slim"))
        assertTrue(bundle.dockerfile.contains("uvicorn"))
        assertTrue(bundle.dockerfile.contains("app:app"))

        // Validate Requirements
        assertTrue(bundle.requirementsTxt.contains("fastapi"))
        assertTrue(bundle.requirementsTxt.contains("scikit-learn"))

        // Validate Scripts
        assertTrue(bundle.launchScriptSh.contains("uvicorn"))
        assertTrue(bundle.launchScriptPs1.contains("uvicorn"))
        assertTrue(bundle.clientTestPy.contains("BASE_URL") && bundle.clientTestPy.contains("/predict"))
    }

    @Test
    fun `test export bundle to directory writes all target artifacts`(@TempDir tempDir: File) {
        val bundle = ModelDeploymentPackager.generateBundle(
            modelName = "credit_risk_onnx",
            framework = "ONNX",
            inputFeatures = listOf("income", "debt", "fico_score")
        )

        val outDir = File(tempDir, "service_export")
        val writtenFiles = bundle.exportToDirectory(outDir)

        assertEquals(6, writtenFiles.size)
        assertTrue(File(outDir, "app.py").exists())
        assertTrue(File(outDir, "Dockerfile").exists())
        assertTrue(File(outDir, "requirements.txt").exists())
        assertTrue(File(outDir, "run_service.sh").exists())
        assertTrue(File(outDir, "run_service.ps1").exists())
        assertTrue(File(outDir, "client_test.py").exists())

        val appPyContent = File(outDir, "app.py").readText()
        assertTrue(appPyContent.contains("onnxruntime"))
        assertTrue(appPyContent.contains("fico_score"))
    }

    @Test
    fun `test framework specific requirements and inference engines`() {
        val onnxBundle = ModelDeploymentPackager.generateBundle(framework = "ONNX")
        assertTrue(onnxBundle.requirementsTxt.contains("onnxruntime"))
        assertTrue(onnxBundle.appPy.contains("InferenceSession"))

        val torchBundle = ModelDeploymentPackager.generateBundle(framework = "PyTorch")
        assertTrue(torchBundle.requirementsTxt.contains("torch"))

        val safeBundle = ModelDeploymentPackager.generateBundle(framework = "Safetensors")
        assertTrue(safeBundle.requirementsTxt.contains("safetensors"))
    }
}
