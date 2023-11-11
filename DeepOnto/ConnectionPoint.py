from flask import Flask, jsonify
from src.deeponto.main import run

def test_gpu():
    import torch
    from torch.cuda import is_available, get_device_name, current_device
    print(is_available(), get_device_name(current_device()))
    tensor = torch.tensor([3, 4, 5], dtype=torch.int64, device=current_device())
    print(tensor.device)
    return tensor.device


run()
"""
app = Flask(__name__)

@app.route('/start_bertmap', methods=['POST'])
def start_bertmap():
    device = test_gpu()
    run()
    response = jsonify({'device' : str(device)})
    print('jsonify = ', response)
    return response


if __name__ == '__main__':
    run()
    # app.run(host='0.0.0.0', port=7532)

"""