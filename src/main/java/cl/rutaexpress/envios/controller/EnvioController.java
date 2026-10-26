package cl.rutaexpress.envios.controller;

import cl.rutaexpress.envios.dto.*;
import cl.rutaexpress.envios.entity.EstadoEnvio;
import cl.rutaexpress.envios.security.UsuarioActual;
import cl.rutaexpress.envios.service.EnvioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final EnvioService envioService;

    public EnvioController(EnvioService envioService) {
        this.envioService = envioService;
    }

    @PostMapping
    public ResponseEntity<EnvioResponse> crear(
            @Valid @RequestBody CrearEnvioRequest request,
            UsuarioActual usuario) {

        EnvioResponse response = envioService.crear(request, usuario);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvioResponse> obtenerPorId(
            @PathVariable Long id,
            UsuarioActual usuario) {

        return ResponseEntity.ok(
                envioService.obtenerPorId(id, usuario)
        );
    }

    @GetMapping
    public ResponseEntity<List<EnvioResponse>> listar(
            @RequestParam(required = false) EstadoEnvio estado,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fechaDesde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fechaHasta,
            UsuarioActual usuario) {

        return ResponseEntity.ok(
                envioService.listar(
                        estado,
                        fechaDesde,
                        fechaHasta,
                        usuario
                )
        );
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<EnvioResponse> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoRequest request,
            UsuarioActual usuario) {

        return ResponseEntity.ok(
                envioService.actualizarEstado(
                        id,
                        request.estado(),
                        request.servicioId(),
                        usuario
                )
        );
    }

    @PostMapping("/{id}/etiqueta")
    public ResponseEntity<Void> solicitarEtiqueta(
            @PathVariable Long id,
            UsuarioActual usuario) {
        envioService.solicitarGeneracionEtiqueta(id, usuario);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/publico/seguimiento/{codigoSeguimiento}")
    public ResponseEntity<SeguimientoPublicoResponse> obtenerSeguimientoPublico(
            @PathVariable String codigoSeguimiento) {

        return ResponseEntity.ok(
                envioService.obtenerSeguimientoPublico(codigoSeguimiento)
        );
    }

    @GetMapping("/geografia/regiones")
    public ResponseEntity<List<RegionConComunasResponse>> listarCatalogoGeografico() {
        return ResponseEntity.ok(
                envioService.listarCatalogoGeografico()
        );
    }
}
