CREATE TABLE USER_ENVIOS.ENVIOS (
    id                  NUMBER GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1),
    codigo_seguimiento  VARCHAR2(50) NOT NULL,
    rut_remitente       VARCHAR2(20) NOT NULL,
    rut_destinatario    VARCHAR2(20) NOT NULL,
    direccion_origen    VARCHAR2(255) NOT NULL,
    direccion_destino   VARCHAR2(255) NOT NULL,
    peso_kg             NUMBER(10,2) NOT NULL,
    estado              VARCHAR2(30) NOT NULL,
    fecha_creacion      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT pk_envios PRIMARY KEY (id),
    CONSTRAINT uk_envios_codigo_seguimiento UNIQUE (codigo_seguimiento),
    CONSTRAINT chk_envios_peso CHECK (peso_kg > 0),
    CONSTRAINT chk_envios_estado CHECK (
        estado IN (
            'CREADO',
            'ACEPTADO',
            'EN_BODEGA',
            'EN_RUTA',
            'ENTREGADO',
            'CANCELADO'
        )
    )
);

CREATE INDEX USER_ENVIOS.idx_envios_codigo_seguimiento
    ON USER_ENVIOS.ENVIOS (codigo_seguimiento);

CREATE INDEX USER_ENVIOS.idx_envios_rut_remitente
    ON USER_ENVIOS.ENVIOS (rut_remitente);

CREATE INDEX USER_ENVIOS.idx_envios_estado
    ON USER_ENVIOS.ENVIOS (estado);