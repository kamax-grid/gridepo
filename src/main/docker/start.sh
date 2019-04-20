#!/usr/bin/env bash

if [[ -n "$CONF_FILE_PATH" ]] && [ ! -f "$CONF_FILE_PATH" ]; then
    echo "Generating config file $CONF_FILE_PATH"
    touch "CONF_FILE_PATH"

    if [[ -n "$GRID_DOMAIN" ]]; then
        echo "Setting Grid domain to $GRID_DOMAIN"
        echo "domain: '$GRID_DOMAIN'" >> "$CONF_FILE_PATH"
        echo >> "$CONF_FILE_PATH"
    fi

    if [[ -n "$DATA_DIR_PATH" ]]; then
        echo "Setting Data path to $DATA_DIR_PATH"
        echo "storage:" >> "$CONF_FILE_PATH"
        echo "  data: '$DATA_DIR_PATH'" >> "$CONF_FILE_PATH"
    fi

    if [[ -n "$DATABASE_CONNECTION" ]]; then
        echo "Setting Database configuration"
        echo "  database:" >> "$CONF_FILE_PATH"
        echo "    type: 'postgresql'" >> "$CONF_FILE_PATH"
        echo "    connection: '$SQLITE_DATABASE_PATH'" >> "$CONF_FILE_PATH"
        echo >> "$CONF_FILE_PATH"
    fi

    echo
fi

exec java -jar /app/gridepo.jar -c /etc/gridepo/gridepo.yaml
