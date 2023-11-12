from __future__ import annotations

import os
from typing import Optional
from yacs.config import CfgNode
from DeepOnto.src.deeponto.utils import FileUtils

DEFAULT_CONFIG_FILE = os.path.join(os.path.dirname(__file__), "default_config.yaml")

def load_bertmap_config(config_file: Optional[str] = None):
    """Load the BERTMap configuration in `.yaml`. If the file
    is not provided, use the default configuration.
    """
    if not config_file:
        config_file = DEFAULT_CONFIG_FILE
        print(f"Use the default configuration at {DEFAULT_CONFIG_FILE}.")
    if not config_file.endswith(".yaml"):
        raise RuntimeError("Configuration file should be in `yaml` format.")
    return CfgNode(FileUtils.load_file(config_file))



def save_bertmap_config(config: CfgNode, config_file: str):
    """Save the BERTMap configuration in `.yaml`."""
    with open(config_file, "w") as c:
        config.dump(stream=c, sort_keys=False, default_flow_style=False)


