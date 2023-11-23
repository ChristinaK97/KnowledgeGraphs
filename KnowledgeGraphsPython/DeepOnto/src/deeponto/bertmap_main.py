# documentation
# https://krr-oxford.github.io/DeepOnto/bertmap/
import os.path
from os.path import exists
from pathlib import Path
from typing import Union

MAP_TO_DO  = "map_to_do"
MAP_TO_DPV = "map_to_dpv"


def run_bertmap_pipeline(
        use_case: str,
        dataset_name: str,
        base_output_dir: str,
        mode: Union[MAP_TO_DO, MAP_TO_DPV],
        POntology_path: str,
        DOntology_path: str,
        DPV_path: str = None
):
    map_to_do_mode = mode == MAP_TO_DO
    # Set config -------------------------------------------------------------------------------------------------------
    if not map_to_do_mode and DPV_path is None or DPV_path=='null':
        raise FileNotFoundError(f"Mode is {mode} but DPV_path eq {DPV_path}")

    output_path = os.path.join(str(Path(base_output_dir)), use_case)
    # print("OUTPUT PATH = ", output_path)
    # eg. resources/fintech/bertmap/EPIBANK/map_to_do/config.yaml
    config_file_path = os.path.join(output_path, 'bertmap', dataset_name, mode, 'config.yaml')

    if not exists(config_file_path):
        print(f"Not found config file for use case = '{use_case}', dataset = {dataset_name} and task = '{mode}' in path = '{config_file_path}'. Loading default parameters."
              f"\nNote that default parameters are available only for DOntology in [FIBO, SNOMED], and/or mapping to DPV for PII identification.", sep="")
        config = get_default_configuration(mode, DOntology_path, use_case)
    else:
        from DeepOnto.src.deeponto.align.bertmap.config_file_handler import load_bertmap_config
        config = load_bertmap_config(config_file_path)
        print(f"Found config file for use case = '{use_case}', dataset = {dataset_name} and task = '{mode}' in path = '{config_file_path}' and loaded successfully.")

    config.output_path = output_path
    config.dataset_name = dataset_name

    # Start jvm --------------------------------------------------------------------------------------------------------
    def startJVM(memory: str = '8g'):
        from DeepOnto.src.deeponto import init_jvm
        # initialise JVM for python-java interaction
        import jpype
        if not jpype.isJVMStarted():
            init_jvm(memory)

    startJVM(config.jvm_max_memory)

    # Set ontologies ---------------------------------------------------------------------------------------------------
    from DeepOnto.src.deeponto.onto import Ontology

    src_onto_path = str(Path(POntology_path))
    tgt_onto_path = str(Path(DOntology_path)) if map_to_do_mode else str(Path(DPV_path))
    if not map_to_do_mode:
        config.auxiliary_ontos = [str(Path(DOntology_path))]

    src_onto = Ontology(src_onto_path, config.reasoner)
    tgt_onto = Ontology(tgt_onto_path, config.reasoner)

    # Run BertMap pipeline ---------------------------------------------------------------------------------------------
    from DeepOnto.src.deeponto.align.bertmap.pipeline import BERTMapPipeline
    return BERTMapPipeline(src_onto, tgt_onto, config).extractBertMapMappings()



# ===========================================================================================================

def get_default_configuration(
        mode: Union[MAP_TO_DO, MAP_TO_DPV],
        DOntology_path: str,
        use_case: str
):
    mode_is_map_to_do = mode == MAP_TO_DO
    do_is_fibo = 'fibo' in DOntology_path.lower()
    use_case_is_health = "health" == use_case

    # Load CONFIG ==========================================================================================
    from DeepOnto.src.deeponto.align.bertmap.config_file_handler import DEFAULT_CONFIG_FILE, load_bertmap_config

    config = load_bertmap_config(DEFAULT_CONFIG_FILE)
    config.mode = mode
    config.dataset_name = ""
    config.jvm_max_memory = '2g' if do_is_fibo else '4g'
    config.use_wordnet = do_is_fibo

    # annotation properties ================================================================================

    config.annotation_property_iris.source = [
        'http://www.w3.org/2000/01/rdf-schema#label',
        'http://www.w3.org/2004/02/skos/core#prefLabel',
        'http://www.w3.org/2004/02/skos/core#altLabel'
    ]

    if do_is_fibo:
        DO_annotation_iris = [
            'http://www.w3.org/2000/01/rdf-schema#label',
            'https://www.omg.org/spec/Commons/AnnotationVocabulary/synonym',
            'https://www.omg.org/spec/Commons/AnnotationVocabulary/abbreviation',
            'https://www.omg.org/spec/Commons/AnnotationVocabulary/acronym'
        ]
        config.annotation_property_iris.additional_annotation_iris = [
            'http://www.w3.org/2004/02/skos/core#definition',
            'https://www.omg.org/spec/Commons/AnnotationVocabulary/explanatoryNote',
            'http://www.w3.org/2004/02/skos/core#editorialNote',
            'http://www.w3.org/2004/02/skos/core#example',
            'http://www.w3.org/2004/02/skos/core#note'
        ]

    elif use_case_is_health:
        DO_annotation_iris = [
            'http://www.w3.org/2004/02/skos/core#altLabel',
            'http://www.w3.org/2004/02/skos/core#prefLabel'
        ]
        config.annotation_property_iris.additional_annotation_iris = [
            'http://www.w3.org/2004/02/skos/core#definition'
        ]

    if mode_is_map_to_do:
        config.annotation_property_iris.target = DO_annotation_iris
    else:
        config.annotation_property_iris.aux = DO_annotation_iris

    # reasoner ============================================================================================
    config.reasoner = 'Pellet' if do_is_fibo else 'Elk'  # for snomed

    # training parameters ================================================================================
    config.bert.pretrained_path = \
        'monologg/biobert_v1.1_pubmed' if use_case_is_health else \
        'yiyanghkust/finbert-pretrain'


    # TODO replace temporary number of epochs
    config.bert.num_epochs_for_training = 3.0 if do_is_fibo else 5.0
    config.bert.batch_size_for_training = 32
    config.bert.batch_size_for_prediction = 32
    # config.bert.resume_training = False

    config.global_matching.num_raw_candidates = 200 # 200 if do_is_fibo else 400
    config.global_matching.num_best_predictions = 20
    config.global_matching.mapping_extension_threshold = 0.85
    config.global_matching.mapping_filtered_threshold = 0.90
    config.global_matching.run_logmap_repair = False

    if not mode_is_map_to_do:
        config.annotation_property_iris.target = [
            'http://www.w3.org/2000/01/rdf-schema#label'
        ]
        config.annotation_property_iris.additional_annotation_iris += [
            'http://purl.org/dc/terms/description'
        ]
    return config

# ================================================================================================

def run_outside_flask():
    HEALTH = 'health'
    FINTECT = 'fintech'
    base = 'C:\\Users\\karal\\progr\\onto_workspace\\Ontologies\\'
    FIBO_FILE = 'FIBOLt.owl'
    SNOMED_FILE = 'SNOMED_rdfxml.rdf'
    FINTECH_PO = 'EPIBANKPO.ttl'
    HEALTH_PO = 'medcsv_with_abbrevExpansions.ttl'
    # -----------------------------------------------------------
    use_case = FINTECT
    DOntology = base + FIBO_FILE
    POntology = base + 'POntologies\\' + FINTECH_PO
    DPV = base + 'dpv-pii.ttl'

    base_output_path = 'resources\\'
    mode = MAP_TO_DPV
    # -------------------------------------------------------------
    bertmap_mappings = run_bertmap_pipeline(
        use_case=use_case,
        dataset_name= "EPIBANK",
        base_output_dir=base_output_path,
        mode=mode,
        POntology_path=POntology,
        DOntology_path=DOntology,
        DPV_path=DPV
    )
    print(bertmap_mappings)
# ================================================================================================

