from flask import Flask, jsonify, request
from src.deeponto.main import run_pipeline, MAP_TO_DO, MAP_TO_DPV

def test_gpu():
    import torch
    from torch.cuda import is_available, get_device_name, current_device
    print(is_available(), get_device_name(current_device()))
    tensor = torch.tensor([3, 4, 5], dtype=torch.int64, device=current_device())
    return tensor.device

def run_outside_flask():
    HEALTH = 'health'
    FINTECT = 'fintech'
    base = 'C:\\Users\\karal\\progr\\onto_workspace\\Ontologies\\'
    FIBO_FILE = 'FIBOLt.owl'
    SNOMED_FILE = 'SNOMED-CT-International-072023.owl'
    # -----------------------------------------------------------
    use_case = FINTECT
    DOntology = base + FIBO_FILE
    POntology = base + 'POntologies\\' + 'EPIBANKPO.ttl'
    DPV = base + 'dpv-pii.ttl'

    base_output_path = 'resources\\'
    mode = MAP_TO_DPV
    # -------------------------------------------------------------
    bertmap_mappings = run_pipeline(
        use_case=use_case,
        base_output_path=base_output_path,
        mode=mode,
        POntology_path=POntology,
        DOntology_path=DOntology,
        DPV_path=DPV
    )
    print(bertmap_mappings)
# ================================================================================================


app = Flask(__name__)

@app.route('/start_bertmap', methods=['POST'])
def start_bertmap():
    print(test_gpu())
    data = request.get_json()
    bertmap_mappings = run_pipeline(
        use_case            = data.get('use_case'),
        base_output_path    = data.get('base_output_path'),
        mode                = MAP_TO_DO if data.get('run_for_do_mapping') else MAP_TO_DPV,
        POntology_path      = data.get('POntology_path'),
        DOntology_path      = data.get('DOntology_path'),
        DPV_path            = data.get('DPV_path')
    )
    response = jsonify(bertmap_mappings)
    print('jsonify = ', response)
    return response


if __name__ == '__main__':
    # run_outside_flask()
    app.run(host='0.0.0.0', port=7532)
