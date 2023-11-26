"""
Added by KGs
also changes in the
- API_endpoint.py : start piis consumer thread
->
from piis_consumer_thread import consume_piis_messages
# Start piis consumer thread
piis_consumer_thread = threading.Thread(target=consume_piis_messages)
piis_consumer_thread.daemon = True
piis_consumer_thread.start()

- Dockerfile : copy this file inside the container
->
COPY API_endpoint.py variables.py functions.py piis_consumer_thread.py ./

--------------KAFKA PIIS CONSUMER-thread---------------------
 Consume the message from piisTopic kafka topic and then save
 the piis results json to a MongoDB collection
 ------------------------------------------------------------
"""
from typing import Callable, Union
from confluent_kafka import Producer, Consumer, KafkaException
from aiokafka import AIOKafkaConsumer   # pip install aiokafka
import asyncio                          # pip install asyncio
import time
import sys

# import functions from the API_node project:
from functions import deserializer
from variables import db, kafka_bootstrap_servers

# variables:
# ---------
# The MongoDB doc collection where the piis results json will be stored
piis_collection:str = "piis_collection"

# The kafka topic from which the piis json will be consumed
# The topic has been created by the knowledge-graphs-main service
piis_kafka_topic:str = "piisTopic"

# The group of the piis consumer
piis_consumer_group_id: str = "piis_consumer_group"
# -----------------------------------------------------------------------------

# 4
def save_to_mongodb(kafka_message: Union[list, dict], collection: str):
    try:
        print(f"Inserting kafka message to MongoDB collection {collection}")
        db[collection].insert_one(kafka_message)
    except KafkaException as e:
        print(f"KafkaException: {str(e)}")


# 3
# Function to create a Kafka consumer for the messages published by the knowledge graphs producer
# with client id = 'knowledge-graphs' in topic 'piisTopic'
def create_kafka_consumer(topic:str, consumer_group_id:str):
    return AIOKafkaConsumer(
        topic,
        bootstrap_servers=kafka_bootstrap_servers,
        value_deserializer=deserializer,
        group_id=consumer_group_id
    )

# 2
async def async_consume(create_consumer_func: Callable, topic:str, consumer_group_id:str, mongo_collection:str):
    consumer = create_consumer_func(topic, consumer_group_id)
    await consumer.start()
    try:
        # Consume messages
        async for msg in consumer:
            kafka_message = msg.value
            print(f"consumer listening on topic = {topic} consumed message {type(kafka_message)}. Size = {sys.getsizeof(kafka_message)} bytes.")
            save_to_mongodb(kafka_message, mongo_collection)

    finally:
        # Will leave consumer group; perform autocommit if enabled.
        print('fine')
        await consumer.stop()

# 1
def consume_piis_messages():
    time.sleep(30)
    print('\n Thread ready \n')
    asyncio.run(
        async_consume(create_consumer_func=create_kafka_consumer,
                      topic=piis_kafka_topic,
                      consumer_group_id=piis_consumer_group_id,
                      mongo_collection=piis_collection)
    )

#-----------------END--------KAFKA PIIS CONSUMER-thread---------------------