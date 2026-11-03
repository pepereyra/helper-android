@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.helper.mobile.ui.mechanic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.mobile.data.network.ConnectivityObserver
import com.helper.mobile.domain.model.Offer
import com.helper.mobile.domain.repository.AcceptResult
import com.helper.mobile.domain.repository.AuthRepository
import com.helper.mobile.domain.repository.AvailabilityResult
import com.helper.mobile.domain.repository.LocationProvider
import com.helper.mobile.domain.repository.MechanicStatusRepository
import com.helper.mobile.domain.repository.OfferRepository
import com.helper.mobile.domain.usecase.TakeOfferUseCase
import com.helper.mobile.ui.common.OfflineBanner
import com.helper.mobile.ui.common.asDistance
import com.helper.mobile.ui.common.asHour
import com.helper.mobile.ui.common.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MechanicViewModel @Inject constructor(
    private val offerRepository: OfferRepository,
    private val takeOffer: TakeOfferUseCase,
    private val auth: AuthRepository,
    private val status: MechanicStatusRepository,
    private val location: LocationProvider,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    val available = MutableStateFlow(false)
    val changingAvailability = MutableStateFlow(false)

    val offers: StateFlow<List<Offer>> =
        offerRepository.observeOffers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val online: StateFlow<Boolean> =
        connectivity.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val message = MutableStateFlow<String?>(null)
    val taken = MutableStateFlow<Offer?>(null)

    init {
        // Las ofertas se refrescan cada 10 segundos mientras la pantalla está abierta.
        viewModelScope.launch {
            while (isActive) {
                if (online.value) offerRepository.refresh()
                delay(10_000)
            }
        }
    }

    fun take(offer: Offer) {
        viewModelScope.launch {
            message.value = when (takeOffer(offer.assistanceId)) {
                AcceptResult.Accepted -> {
                    taken.value = offer
                    null
                }
                AcceptResult.AlreadyTaken -> "Otro mecánico ya lo tomó."
                AcceptResult.NotAllowed -> {
                    // El servidor no lo ve aprobado: un token nuevo trae el estado real y, si hace falta,
                    // la app vuelve a la pantalla de revisión.
                    auth.refreshSession()
                    "Tu cuenta todavía no está aprobada para tomar pedidos."
                }
                AcceptResult.NoConnection -> "Para tomar un pedido necesitás conexión."
                is AcceptResult.Failed -> "No se pudo tomar el pedido. Probá de nuevo."
            }
        }
    }

    /** Ponerse disponible manda la ubicación actual; el servidor responde 403 si la cuenta no está aprobada. */
    fun setAvailable(wanted: Boolean) {
        changingAvailability.value = true
        viewModelScope.launch {
            val here = location.currentLocation()
            message.value = if (here == null) {
                "Necesitamos tu ubicación para mostrarte pedidos cercanos."
            } else {
                when (val result = status.setAvailable(wanted, here)) {
                    AvailabilityResult.Done -> {
                        available.value = wanted
                        null
                    }
                    AvailabilityResult.NotApproved -> {
                        auth.refreshSession()
                        "Tu cuenta todavía no está aprobada."
                    }
                    AvailabilityResult.NoConnection -> "Sin conexión. Probá de nuevo cuando vuelva la señal."
                    is AvailabilityResult.Rejected -> result.reason
                }
            }
            changingAvailability.value = false
        }
    }

    fun pass(offer: Offer) {
        viewModelScope.launch { offerRepository.decline(offer.assistanceId) }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}

/** RF04: pedidos cercanos. Tomar requiere conexión: gana el primero que acepta en el servidor. */
@Composable
fun MechanicScreen(viewModel: MechanicViewModel = hiltViewModel()) {
    val offers by viewModel.offers.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val taken by viewModel.taken.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val changing by viewModel.changingAvailability.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Pedidos cerca", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = viewModel::logout) { Text("Salir") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (available) "Estás disponible" else "No estás disponible", style = MaterialTheme.typography.titleMedium)
            Switch(checked = available, onCheckedChange = viewModel::setAvailable, enabled = online && !changing)
        }
        if (!online) OfflineBanner("Sin conexión. No podés tomar pedidos hasta que vuelva la señal.")
        message?.let { Card(onClick = viewModel::dismissMessage) { Text(it, Modifier.padding(12.dp)) } }
        taken?.let {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Tomaste el pedido", style = MaterialTheme.typography.titleMedium)
                    Text("${it.problemLabel} · ${it.vehicleType.label()} · a ${it.distanceMeters.asDistance()}")
                }
            }
        }
        if (offers.isEmpty()) {
            Text(
                if (available) {
                    "No hay pedidos cerca por ahora. Te mostramos los nuevos apenas aparezcan."
                } else {
                    "Ponete disponible para recibir pedidos cercanos."
                },
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(offers, key = { it.assistanceId }) { offer ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(offer.problemLabel, style = MaterialTheme.typography.titleMedium)
                        Text("${offer.vehicleType.label()} · a ${offer.distanceMeters.asDistance()}")
                        Text("Disponible hasta las ${offer.expiresAt.asHour()}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.pass(offer) }) { Text("Pasar") }
                            Button(onClick = { viewModel.take(offer) }, enabled = online) { Text("Tomar") }
                        }
                    }
                }
            }
        }
    }
}

/** Mientras un administrador no lo apruebe, el servidor no le deja ponerse disponible ni aceptar. */
@Composable
fun PendingVerificationScreen(rejected: Boolean, onCheckAgain: () -> Unit, onLogout: () -> Unit) {
    // Mientras está en revisión se renueva el token cada 30 segundos: cuando lo aprueban, la pantalla cambia sola.
    if (!rejected) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                onCheckAgain()
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (rejected) "No pudimos aprobar tu cuenta" else "Tu cuenta está en revisión",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (rejected) {
                "Revisá la documentación que cargaste o escribinos para volver a intentarlo."
            } else {
                "Estamos validando tu DNI y tu CUIT. Cuando te aprobemos vas a poder ver y tomar pedidos."
            },
        )
        if (!rejected) OutlinedButton(onClick = onCheckAgain) { Text("Revisar ahora") }
        TextButton(onClick = onLogout) { Text("Salir") }
    }
}
