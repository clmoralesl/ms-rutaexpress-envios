package cl.rutaexpress.envios.controller;

import cl.rutaexpress.envios.dto.ActualizarEstadoRequest;
import cl.rutaexpress.envios.dto.EnvioResponse;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.exception.CapacidadFlotaAgotadaException;
import cl.rutaexpress.envios.exception.EnvioNoEncontradoException;
import cl.rutaexpress.envios.exception.GlobalExceptionHandler;
import cl.rutaexpress.envios.exception.TransicionEstadoInvalidaException;
import cl.rutaexpress.envios.service.EnvioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import cl.rutaexpress.envios.dto.EventoSeguimientoDto;
import cl.rutaexpress.envios.dto.SeguimientoPublicoResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnvioController.class)
@Import(GlobalExceptionHandler.class)
class EnvioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnvioService envioService;

    @Test
    @DisplayName("PUT /api/envios/{id}/estado retorna 200 OK en transicion valida")
    void testActualizarEstadoExito() throws Exception {
        ActualizarEstadoRequest request = new ActualizarEstadoRequest(EstadoEnvio.ACEPTADO, 1L);
        EnvioResponse response = new EnvioResponse(
                1L,
                "RE-TEST-100",
                "11111111-1",
                "22222222-2",
                "Origen",
                "Destino",
                new BigDecimal("3.00"),
                EstadoEnvio.ACEPTADO,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(envioService.actualizarEstado(eq(1L), eq(EstadoEnvio.ACEPTADO), eq(1L)))
                .thenReturn(response);

        mockMvc.perform(put("/api/envios/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.codigoSeguimiento").value("RE-TEST-100"));
    }

    @Test
    @DisplayName("PUT /api/envios/{id}/estado retorna 409 Conflict cuando la capacidad de flota esta agotada")
    void testActualizarEstadoCapacidadAgotada() throws Exception {
        ActualizarEstadoRequest request = new ActualizarEstadoRequest(EstadoEnvio.ACEPTADO, 2L);

        when(envioService.actualizarEstado(eq(2L), eq(EstadoEnvio.ACEPTADO), eq(2L)))
                .thenThrow(new CapacidadFlotaAgotadaException(2L));

        mockMvc.perform(put("/api/envios/2/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Capacidad de flota agotada para el servicio ID 2"));
    }

    @Test
    @DisplayName("PUT /api/envios/{id}/estado retorna 400 Bad Request en transicion invalida")
    void testActualizarEstadoTransicionInvalida() throws Exception {
        ActualizarEstadoRequest request = new ActualizarEstadoRequest(EstadoEnvio.ENTREGADO, null);

        when(envioService.actualizarEstado(eq(3L), eq(EstadoEnvio.ENTREGADO), eq(null)))
                .thenThrow(new TransicionEstadoInvalidaException(EstadoEnvio.CREADO, EstadoEnvio.ENTREGADO));

        mockMvc.perform(put("/api/envios/3/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PUT /api/envios/{id}/estado retorna 404 Not Found si el envio no existe")
    void testActualizarEstadoNoEncontrado() throws Exception {
        ActualizarEstadoRequest request = new ActualizarEstadoRequest(EstadoEnvio.CANCELADO, null);

        when(envioService.actualizarEstado(eq(999L), eq(EstadoEnvio.CANCELADO), eq(null)))
                .thenThrow(new EnvioNoEncontradoException(999L));

        mockMvc.perform(put("/api/envios/999/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un envío con ID 999"));
    }

    @Test
    @DisplayName("POST /api/envios/{id}/etiqueta retorna 202 Accepted y solicita emision a q.cmd.label")
    void testSolicitarEtiquetaExito() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/envios/1/etiqueta"))
                .andExpect(status().isAccepted());

        org.mockito.Mockito.verify(envioService).solicitarGeneracionEtiqueta(eq(1L));
    }

    @Test
    @DisplayName("GET /api/envios/publico/seguimiento/{codigo} retorna 200 OK con payload sanitizado")
    void testSeguimientoPublicoExito() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        SeguimientoPublicoResponse response = new SeguimientoPublicoResponse(
                "RTX-2026-0001",
                EstadoEnvio.EN_RUTA,
                "Santiago",
                "Valparaíso",
                ahora.plusHours(24),
                "Envío Express 24h",
                "Flota de Distribución RutaExpress",
                new BigDecimal("2.50"),
                ahora,
                ahora,
                List.of(
                        new EventoSeguimientoDto(EstadoEnvio.CREADO, "Envío registrado en plataforma", ahora),
                        new EventoSeguimientoDto(EstadoEnvio.EN_RUTA, "En transporte hacia la dirección de destino", ahora)
                )
        );

        when(envioService.obtenerSeguimientoPublico(eq("RTX-2026-0001")))
                .thenReturn(response);

        mockMvc.perform(get("/api/envios/publico/seguimiento/RTX-2026-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSeguimiento").value("RTX-2026-0001"))
                .andExpect(jsonPath("$.estadoActual").value("EN_RUTA"))
                .andExpect(jsonPath("$.ciudadOrigen").value("Santiago"))
                .andExpect(jsonPath("$.ciudadDestino").value("Valparaíso"))
                .andExpect(jsonPath("$.servicioContratado").value("Envío Express 24h"))
                .andExpect(jsonPath("$.courierAsignado").value("Flota de Distribución RutaExpress"))
                .andExpect(jsonPath("$.historial").isArray());
    }

    @Test
    @DisplayName("GET /api/envios/publico/seguimiento/{codigo} retorna 404 si el codigo no existe")
    void testSeguimientoPublicoNoEncontrado() throws Exception {
        when(envioService.obtenerSeguimientoPublico(eq("INEXISTENTE")))
                .thenThrow(new EnvioNoEncontradoException("INEXISTENTE"));

        mockMvc.perform(get("/api/envios/publico/seguimiento/INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un envío con código de seguimiento INEXISTENTE"));
    }
}
