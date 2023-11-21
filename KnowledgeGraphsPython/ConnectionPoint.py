import os.path
from os import path
from pathlib import Path

from flask import Flask, jsonify, request

def test_gpu():
    import torch
    from torch.cuda import is_available, get_device_name, current_device
    print(is_available(), get_device_name(current_device()))
    tensor = torch.tensor([3, 4, 5], dtype=torch.int64, device=current_device())
    return tensor.device
# ======================================================================================================================
app = Flask(__name__)

# ======================================================================================================================
# BERTMap ENDPOINT
# ======================================================================================================================
@app.route('/start_bertmap', methods=['POST'])
def start_bertmap():
    from DeepOnto.src.deeponto.bertmap_main import MAP_TO_DPV, MAP_TO_DO, run_bertmap_pipeline
    data = request.get_json()
    print("Start BertMap request with input :\n\t", data)
    bertmap_mappings = run_bertmap_pipeline(
        use_case            = data.get('use_case'),
        dataset_name        = data.get('dataset_name'),
        base_output_dir     = str(Path(f"{base_resources_dir}/DeepOnto")),
        mode                = MAP_TO_DO if data.get('run_for_do_mapping') else MAP_TO_DPV,
        POntology_path      = data.get('pontology_path'),
        DOntology_path      = data.get('dontology_path'),
        DPV_path            = data.get('dpv_path')
    )
    response = jsonify(bertmap_mappings)
    print('jsonify = ', response)
    return response

# ======================================================================================================================
# AAExpansion ENDPOINT
# ======================================================================================================================
@app.route('/start_aa_expansion', methods=['POST'])
def start_aa_expansion():
    from AAExpansion.source.InterpretHeaders import InterpretHeaders

    # AAExpansion files ------------------------------------------------------------------------------------------------
    aa_expansion_base_dir = Path(f"{base_resources_dir}/AAExpansion")
    metaInventoryPath = Path(f"{aa_expansion_base_dir}/Metainventory_Version1.0.0.csv")
    outputPath = Path(f"{aa_expansion_base_dir}/abbrevExpansionResults.json")
    # ------------------------------------------------------------------------------------------------------------------
    request_data = request.get_json()
    headers = request_data.get('inputs', [])
    useScispacyEntityLinker = request_data.get('useScispacyEntityLinker', False)

    print("Start AAExpansion request with input:\n\t# headers = ", len(headers), headers[0:5],
          "...\nuseScispacyEntityLinker =", useScispacyEntityLinker)

    AAExpansionResults = InterpretHeaders(
        aa_expansion_base_dir=aa_expansion_base_dir,
        headers=headers,
        medDictPath=metaInventoryPath,
        outputPath=outputPath,
        useScispacyEntityLinker=useScispacyEntityLinker,
        reset=False
    ).results
    response = jsonify(AAExpansionResults)
    print("jsonify = ", response)
    return response

# ======================================================================================================================


if __name__ == '__main__':
    print(test_gpu())
    script_dir = Path(path.dirname(path.abspath(__file__)))
    base_resources_dir = "/KnowledgeGraphsApp/data" if str(script_dir).startswith("/KnowledgeGraphsApp") else \
                         f"{script_dir.parent}/.KnowledgeGraphsData"
    base_resources_dir = str(Path(base_resources_dir))

    app.run(host='0.0.0.0', port=7531)
