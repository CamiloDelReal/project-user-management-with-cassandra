create keyspace users_database with replication = {'class': 'org.apache.cassandra.locator.SimpleStrategy', 'replication_factor': '1'};

create table roles
(
    id   text,
    name text,
    primary key (id, name)
)
    with caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
     and compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
     and compression = {'chunk_length_in_kb': '16', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
     and dclocal_read_repair_chance = 0.0
     and speculative_retry = '99p';

create index roles__index_name
    on roles (name);
    

create table users
(
    id                 text,
    email              text,
    first_name         text,
    last_name          text,
    protected_password text,
    roles              set<text>,
    primary key (id, email)
)
    with caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
     and compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
     and compression = {'chunk_length_in_kb': '16', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
     and dclocal_read_repair_chance = 0.0
     and speculative_retry = '99p';

create index users__index_email
    on users (email);
    

