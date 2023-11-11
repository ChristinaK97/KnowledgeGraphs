import os.path as path

import torch
from pathlib import Path
from flask import Flask, request, jsonify

isCudaAvailableMessage = \
    "Cuda available : " + torch.cuda.get_device_name(torch.cuda.current_device()) if torch.cuda.is_available() else \
    "Cuda NOT available"
print(isCudaAvailableMessage)
# ----------------------------------------------------------------------------------------------------------------------

metaInventoryPath = Path("/KnowledgeGraphsApp/resources/Metainventory_Version1.0.0.csv")
if not path.exists(metaInventoryPath):
    script_dir = Path(path.dirname(path.abspath(__file__)))
    metaInventoryPath = Path(
        f"{script_dir.parent}/.KnowledgeGraphsResources/AAExpansion/Metainventory_Version1.0.0.csv")

outputPath = Path("resources/abbrevExpansionResults.json")
# ----------------------------------------------------------------------------------------------------------------------

app = Flask(__name__)

@app.route('/start_aa_expansion', methods=['POST'])
def start_aa_expansion():
    from source.InterpretHeaders import InterpretHeaders

    headers = request.get_json()
    print("# headers = ", len(headers), headers[0:5])

    AAExpansionResults  = InterpretHeaders(headers, metaInventoryPath, outputPath, False).results
    response = jsonify(AAExpansionResults)
    print("jsonify = ", response)
    return response


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=7531)


