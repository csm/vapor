CREATE TYPE vapor.stack_frame (
    file text,
    className text;
    method text;
    line int)

CREATE TABLE vapor.log_messages (
    host text,
    time timestamp,
    level int,
    name text,
    message text,
    exception text,
    stack list<vapor.stack_frame>,
    attributes map<ascii, ascii>,
    stargate text,
    PRIMARY KEY (host, time)
) WITH CLUSTERING ORDER BY (time DESC);

CREATE CUSTOM INDEX IF NOT EXISTS log_index ON vapor.log_messages (stargate)
    USING 'com.tuplejump.stargate.RowIndex'
    WITH options = {
        'sg_options':'{
            "fields":{
                "host":{},
                "time":{},
                "level":{},
                "name":{},
                "message":{},
                "exception":{},
                "stack":{},
                "attributes":{}
            }
        }'
    };