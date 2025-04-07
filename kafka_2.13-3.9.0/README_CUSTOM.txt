GENERATE KAFKA STORAGES STORAGES WITH RANDOM UUID
./bin/kafka-storage.sh random-uuid
./bin/kafka-storage.sh format -t [GENERATED_TOKEN] -c config/kraft/server-1.properties
./bin/kafka-storage.sh format -t [GENERATED_TOKEN] -c config/kraft/server-2.properties
./bin/kafka-storage.sh format -t [GENERATED_TOKEN] -c config/kraft/server-3.properties

START 3 KAFKA BROKERS
./bin/kafka-server-start.sh config/kraft/server-1.properties
./bin/kafka-server-start.sh config/kraft/server-2.properties
./bin/kafka-server-start.sh config/kraft/server-3.properties

STOP ALL KAFKA BROKERS
./bin/kafka-server-stop.sh

CREATE TOPICS
./kafka-topics.sh --create --topic topic1 --partitions 3 --replication-factor 3 --bootstrap-server localhost:9092,localhost:9094
./kafka-topics.sh --create --topic topic2 --partitions 3 --replication-factor 3 --bootstrap-server localhost:9092,localhost:9094
./bin/kafka-topics.sh --create --topic insync-topic --partitions 3 --replication-factor 3 --bootstrap-server localhost:9092 --config min.insync.replicas=2


LIST TOPICS
./kafka-topics.sh --list --bootstrap-server localhost:9092
LIST TOPICS (DETAILED)
./kafka-topics.sh --describe --bootstrap-server localhost:9092

DELETE TOPIC
./kafka-topics.sh --delete --topic topic1 --bootstrap-server localhost:9092

PRODUCE MESSAGES WITHOUT A KEY
./kafka-console-producer.sh --bootstrap-server localhost:9092,localhost:9094 --topic my-topic

PRODUCE MESSAGES WITH A KEY SEPARATED WITH ":"
./kafka-console-producer.sh --bootstrap-server localhost:9092,localhost:9094 --topic my-topic --property "parse.key=true" --property "key.separator=:"

READ ALL MESSAGES FROM THE BEGINNING
./kafka-console-consumer.sh --topic my-topic --from-beginning --bootstrap-server localhost:9092

READ NEW MESSAGES ONLY
./kafka-console-consumer.sh --topic my-topic --bootstrap-server localhost:9092

READ NEW MESSAGES WITH PRINITING KEY-VALUE
./kafka-console-consumer.sh --topic my-topic --bootstrap-server localhost:9092 --property "print.key=true"
./kafka-console-consumer.sh --topic my-topic --bootstrap-server localhost:9092 --property "print.key=true" --property "print.value=true" --from-beginning
./kafka-console-consumer.sh --topic my-topic --bootstrap-server localhost:9092 --property "print.key=true" --property "print.value=false" --from-beginning

UPDATE TOPIC WITH A NEW CONFIG
./bin/kafka-configs.sh --bootstrap-server localhost:9092 --alter --entity-type topics --entity-name topic2 --add-config min.insync.replicas=2


EXECUTE SCRIPT FROM LOCAL MACHINE IN DOCKER
docker-compose exec kafka-1 /opt/bitnami/kafka/bin/kafka-topics.sh --list --bootstrap-server 172.17.0.1:9092
