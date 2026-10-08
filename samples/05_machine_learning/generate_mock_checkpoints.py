"""
Generates valid mock .safetensors checkpoint files for testing
Jörmungandr's Model Checkpoint & Neural Graph Inspector.
"""

import os
import json
import struct

def create_mock_safetensors():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    model_path = os.path.join(out_dir, "resnet50_sample.safetensors")

    header = {
        "__metadata__": {
            "format": "pt",
            "model_architecture": "ResNet-50",
            "created_by": "Jormungandr AI Studio"
        },
        "conv1.weight": {
            "dtype": "F32",
            "shape": [64, 3, 7, 7],
            "data_offsets": [0, 37632]
        },
        "bn1.weight": {
            "dtype": "F32",
            "shape": [64],
            "data_offsets": [37632, 37888]
        },
        "bn1.bias": {
            "dtype": "F32",
            "shape": [64],
            "data_offsets": [37888, 38144]
        },
        "layer1.0.conv1.weight": {
            "dtype": "F16",
            "shape": [64, 64, 1, 1],
            "data_offsets": [38144, 46336]
        },
        "layer1.0.conv2.weight": {
            "dtype": "F16",
            "shape": [64, 64, 3, 3],
            "data_offsets": [46336, 120064]
        },
        "fc.weight": {
            "dtype": "F32",
            "shape": [1000, 2048],
            "data_offsets": [120064, 8312064]
        },
        "fc.bias": {
            "dtype": "F32",
            "shape": [1000],
            "data_offsets": [8312064, 8316064]
        }
    }

    header_bytes = json.dumps(header).encode("utf-8")
    header_len = len(header_bytes)

    # 8-byte uint64 little-endian length prefix
    len_prefix = struct.pack("<Q", header_len)

    # Dummy buffer for tensor payloads
    payload_len = 8316064
    # To keep file compact in repository while testing header inspection,
    # we can write a small buffer (e.g. 4096 bytes or full mock)
    # The header inspector parses the header bytes and offsets.
    with open(model_path, "wb") as f:
        f.write(len_prefix)
        f.write(header_bytes)
        f.write(b"\x00" * 1024) # Sample payload bytes

    print(f"Generated mock Safetensors file: {model_path} ({header_len} header bytes)")

if __name__ == "__main__":
    create_mock_safetensors()
