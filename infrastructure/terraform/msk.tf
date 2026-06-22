resource "aws_msk_cluster" "banking" {
  cluster_name           = "banking-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = "kafka.m5.large"
    client_subnets  = module.vpc.private_subnets
    storage_info {
      ebs_storage_info { volume_size = 500 }
    }
    security_groups = [aws_security_group.msk.id]
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.banking.arn
    revision = aws_msk_configuration.banking.latest_revision
  }

  open_monitoring {
    prometheus {
      jmx_exporter  { enabled_in_broker = true }
      node_exporter  { enabled_in_broker = true }
    }
  }
}

resource "aws_msk_configuration" "banking" {
  name = "banking-kafka-config"
  kafka_versions = ["3.6.0"]
  server_properties = <<PROPERTIES
auto.create.topics.enable=false
default.replication.factor=3
min.insync.replicas=2
num.partitions=6
log.retention.hours=168
log.retention.bytes=1073741824
PROPERTIES
}

resource "aws_security_group" "msk" {
  name_prefix = "banking-msk-"
  vpc_id      = module.vpc.vpc_id
  ingress {
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }
}
