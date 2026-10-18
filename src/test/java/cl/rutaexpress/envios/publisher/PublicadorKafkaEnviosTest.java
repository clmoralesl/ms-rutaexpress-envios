package cl.rutaexpress.envios.publisher;

import cl.rutaexpress.envios.config.KafkaConfig;
import cl.rutaexpress.envios.dto.EventoEnvioKafka;
import cl.rutaexpress.envios.entity.Envio;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicadorKafkaEnviosTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<EventoEnvioKafka> eventoCaptor;

    private PublicadorKafkaEnvios publicador;

    @BeforeEach
    void setUp() {
        publicador = new PublicadorKafkaEnvios(kafkaTemplate, KafkaConfig.TOPICO_SHIPMENTS_EVENTS);
    }

    private Envio crearEnvio(Long id, String codigo) {
        Envio envio = new Envio(
                codigo,
                "12345678-5",
                "98765432-1",
                "Av. Alameda 100",
                "Av. Vitacura 200",
                new BigDecimal("5.50"),
                EstadoEnvio.CREADO
        );
        try {
            var field = Envio.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(envio, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return envio;
    }

    @Test
    @DisplayName("Publicacion exitosa en Kafka con clave envioId")
    void testPublicarEventoExitoso() {
        Envio envio = crearEnvio(101L, "RE-KAFKA-001");
        EventoEnvioKafka evento = EventoEnvioKafka.deTransicion(
                envio,
                null,
                EstadoEnvio.CREADO,
                "operador@rutaexpress.cl",
                "OPERADOR",
                null
        );

        TopicPartition tp = new TopicPartition(KafkaConfig.TOPICO_SHIPMENTS_EVENTS, 0);
        RecordMetadata metadata = new RecordMetadata(tp, 0, 0, System.currentTimeMillis(), 0, 0);
        SendResult<String, Object> sendResult = new SendResult<>(null, metadata);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq(KafkaConfig.TOPICO_SHIPMENTS_EVENTS), eq("101"), any(EventoEnvioKafka.class)))
                .thenReturn(future);

        assertDoesNotThrow(() -> publicador.publicarEvento(evento));

        verify(kafkaTemplate, times(1)).send(
                eq(KafkaConfig.TOPICO_SHIPMENTS_EVENTS),
                eq("101"),
                eventoCaptor.capture()
        );

        EventoEnvioKafka eventoEnviado = eventoCaptor.getValue();
        assertEquals(101L, eventoEnviado.envioId());
        assertEquals(EstadoEnvio.CREADO, eventoEnviado.estadoNuevo());
        assertEquals("operador@rutaexpress.cl", eventoEnviado.usuarioEmail());
        assertEquals("OPERADOR", eventoEnviado.usuarioRol());
    }

    @Test
    @DisplayName("Resiliencia ante fallo sincrono de Kafka: no propaga excepcion")
    void testPublicarEventoSincronoFalla() {
        Envio envio = crearEnvio(102L, "RE-KAFKA-002");
        EventoEnvioKafka evento = EventoEnvioKafka.deTransicion(
                envio,
                EstadoEnvio.CREADO,
                EstadoEnvio.ACEPTADO,
                null,
                null,
                null
        );

        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new RuntimeException("Error de conexion hacia Kafka"));

        assertDoesNotThrow(() -> publicador.publicarEvento(evento));

        verify(kafkaTemplate, times(1)).send(eq(KafkaConfig.TOPICO_SHIPMENTS_EVENTS), eq("102"), any());
    }

    @Test
    @DisplayName("Resiliencia ante fallo asincrono del CompletableFuture: no interrumpe el flujo")
    void testPublicarEventoAsincronoFalla() {
        Envio envio = crearEnvio(103L, "RE-KAFKA-003");
        EventoEnvioKafka evento = EventoEnvioKafka.deTransicion(
                envio,
                EstadoEnvio.ACEPTADO,
                EstadoEnvio.EN_BODEGA,
                null,
                null,
                null
        );

        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Timeout de red en Kafka broker"));

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        assertDoesNotThrow(() -> publicador.publicarEvento(evento));

        verify(kafkaTemplate, times(1)).send(eq(KafkaConfig.TOPICO_SHIPMENTS_EVENTS), eq("103"), any());
    }

    @Test
    @DisplayName("Evento nulo es ignorado de forma segura")
    void testPublicarEventoNulo() {
        assertDoesNotThrow(() -> publicador.publicarEvento(null));
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
