# documentation
# https://krr-oxford.github.io/DeepOnto/bertmap/


def run():
    base = 'C:\\Users\\karal\\progr\\onto_workspace\\Ontologies\\'
    FIBO_FILE = 'FIBOLt.owl'
    SNOMED_FILE = 'SNOMED-CT-International-072023.owl'

    DOntology = FIBO_FILE
    POntology = 'POntologies\\' + 'EPIBANKPO.ttl'

    MAP_TO_DO_TASK = False



    # Load CONFIG ====================================================================
    from src.deeponto.align.bertmap.config_file_handler import load_bertmap_config, DEFAULT_CONFIG_FILE

    def startJVM(memory: str = '8g'):
        from src.deeponto import init_jvm
        # initialise JVM for python-java interaction
        import jpype
        if not jpype.isJVMStarted():
            init_jvm(memory)

    config = load_bertmap_config(DEFAULT_CONFIG_FILE)
    startJVM(config.jvm_max_memory)
    config.output_path = 'resources\\'

    # ================================================================================

    from src.deeponto.onto import Ontology
    from src.deeponto.align.bertmap.pipeline import BERTMapPipeline

    def config_for_do_mapping():
        # Define ontologies
        tgt_onto_path = base + DOntology
        return tgt_onto_path


    def config_for_pii_mapping():
        # Define ontologies
        tgt_onto_path = base + 'dpv-pii.ttl'
        config.annotation_property_iris.target = [
            'http://www.w3.org/2000/01/rdf-schema#label'
        ]
        config.annotation_property_iris.additional_annotation_iris += [
            'http://purl.org/dc/terms/description'
        ]
        # use do for training
        config.auxiliary_ontos = [base + DOntology]
        return tgt_onto_path


    # annotation properties ================================================================================

    config.annotation_property_iris.source = [
        'http://www.w3.org/2000/01/rdf-schema#label',
        'http://www.w3.org/2004/02/skos/core#prefLabel',
        'http://www.w3.org/2004/02/skos/core#altLabel'
    ]


    if DOntology == FIBO_FILE:
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

    elif DOntology == SNOMED_FILE:
        DO_annotation_iris = [
            'http://www.w3.org/2004/02/skos/core#altLabel',
            'http://www.w3.org/2004/02/skos/core#prefLabel'
        ]
        config.annotation_property_iris.additional_annotation_iris = [
            'http://www.w3.org/2004/02/skos/core#definition'
        ]

    if MAP_TO_DO_TASK:
        config.annotation_property_iris.target = DO_annotation_iris
    else:
        config.annotation_property_iris.aux = DO_annotation_iris



    # reasoner ============================================================================================
    config.reasoner = 'Pellet' if DOntology == FIBO_FILE else 'Elk'  # for snomed


    # training parameters ================================================================================
    config.bert.pretrained_path = \
        'yiyanghkust/finbert-pretrain' if DOntology == FIBO_FILE else \
        'monologg/biobert_v1.1_pubmed'



    config.bert.num_epochs_for_training = 1.0
    config.bert.batch_size_for_training = 32
    config.bert.batch_size_for_prediction = 32


    config.number_raw_candidates = 400
    config.global_matching.num_best_predictions = 20
    config.global_matching.mapping_extension_threshold = 0.85
    config.global_matching.mapping_filtered_threshold = 0.90
    config.global_matching.run_logmap_repair = False
    # config.bert.resume_training = False

    print(config)

    # ================================================================================

    # Load Ontologies and run bertmap pipeline

    src_onto_path = base + POntology
    tgt_onto_path = config_for_do_mapping() if MAP_TO_DO_TASK else config_for_pii_mapping()

    src_onto = Ontology(src_onto_path, config.reasoner)
    tgt_onto = Ontology(tgt_onto_path, config.reasoner)
    BERTMapPipeline(src_onto, tgt_onto, config)



