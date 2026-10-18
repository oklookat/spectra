package com.oklookat.spectra.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oklookat.spectra.data.repository.SettingsRepository
import com.oklookat.spectra.model.CustomDnsConfig
import com.oklookat.spectra.model.CustomRoutingConfig
import com.oklookat.spectra.model.DnsServerConfig
import com.oklookat.spectra.model.RoutingRuleConfig
import com.oklookat.spectra.service.XrayVpnService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DnsRoutingUiState(
    val selectedTab: Int = 0, // 0 = DNS, 1 = Routing
    val customDnsConfig: CustomDnsConfig = CustomDnsConfig.default(),
    val customRoutingConfig: CustomRoutingConfig = CustomRoutingConfig.default(),
    val isDnsDialogVisible: Boolean = false,
    val editingDnsServer: DnsServerConfig? = null,
    val isRoutingRuleDialogVisible: Boolean = false,
    val editingRoutingRule: RoutingRuleConfig? = null,
)

enum class RoutingPreset(
    val titleRu: String,
    val createRule: () -> RoutingRuleConfig
) {
    BYPASS_PRIVATE_IP(
        titleRu = "Локальные IP напрямую (geoip:private -> Direct)",
        createRule = {
            RoutingRuleConfig(
                name = "Private IP Direct",
                outboundTag = "direct-out",
                ip = listOf("geoip:private"),
                enabled = true
            )
        }
    ),
    BYPASS_RU_DOMAINS(
        titleRu = "Домены RU напрямую (geosite:ru -> Direct)",
        createRule = {
            RoutingRuleConfig(
                name = "RU Domains Direct",
                outboundTag = "direct-out",
                domains = listOf("geosite:ru"),
                enabled = true
            )
        }
    ),
    BYPASS_CATEGORY_RU_DOMAINS(
        titleRu = "Категория RU напрямую (geosite:category-ru -> Direct)",
        createRule = {
            RoutingRuleConfig(
                name = "Category RU Direct",
                outboundTag = "direct-out",
                domains = listOf("geosite:category-ru"),
                enabled = true
            )
        }
    ),
    BYPASS_RU_IP(
        titleRu = "IP РФ напрямую (geoip:ru -> Direct)",
        createRule = {
            RoutingRuleConfig(
                name = "RU IPs Direct",
                outboundTag = "direct-out",
                ip = listOf("geoip:ru"),
                enabled = true
            )
        }
    ),
    BLOCK_ADS(
        titleRu = "Блокировка рекламы (geosite:category-ads-all -> Block)",
        createRule = {
            RoutingRuleConfig(
                name = "Block Advertising",
                outboundTag = "block-out",
                domains = listOf("geosite:category-ads-all"),
                enabled = true
            )
        }
    ),
    BITTORRENT_DIRECT(
        titleRu = "Торрент напрямую (bittorrent -> Direct)",
        createRule = {
            RoutingRuleConfig(
                name = "BitTorrent Direct",
                outboundTag = "direct-out",
                protocols = listOf("bittorrent"),
                enabled = true
            )
        }
    )
}

@HiltViewModel
class DnsRoutingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var uiState by mutableStateOf(DnsRoutingUiState())
        private set

    private val _events = MutableSharedFlow<MainUiEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                uiState = uiState.copy(
                    customDnsConfig = settings.customDnsConfig,
                    customRoutingConfig = settings.customRoutingConfig
                )
            }
        }
    }

    fun selectTab(index: Int) {
        uiState = uiState.copy(selectedTab = index)
    }

    // --- DNS Actions ---

    fun setDnsQueryStrategy(strategy: String) {
        val updated = uiState.customDnsConfig.copy(queryStrategy = strategy)
        saveDnsConfig(updated)
    }

    fun openAddDnsServerDialog() {
        uiState = uiState.copy(
            isDnsDialogVisible = true,
            editingDnsServer = null
        )
    }

    fun openEditDnsServerDialog(server: DnsServerConfig) {
        uiState = uiState.copy(
            isDnsDialogVisible = true,
            editingDnsServer = server
        )
    }

    fun closeDnsServerDialog() {
        uiState = uiState.copy(
            isDnsDialogVisible = false,
            editingDnsServer = null
        )
    }

    fun saveDnsServer(address: String) {
        val currentServers = uiState.customDnsConfig.servers.toMutableList()
        val editing = uiState.editingDnsServer

        if (editing != null) {
            val idx = currentServers.indexOfFirst { it.id == editing.id }
            if (idx != -1) {
                currentServers[idx] = editing.copy(address = address.trim())
            }
        } else {
            currentServers.add(DnsServerConfig(address = address.trim(), enabled = true))
        }

        saveDnsConfig(uiState.customDnsConfig.copy(servers = currentServers))
        closeDnsServerDialog()
    }

    fun toggleDnsServer(id: String, enabled: Boolean) {
        val currentServers = uiState.customDnsConfig.servers.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveDnsConfig(uiState.customDnsConfig.copy(servers = currentServers))
    }

    fun deleteDnsServer(id: String) {
        val currentServers = uiState.customDnsConfig.servers.filterNot { it.id == id }
        saveDnsConfig(uiState.customDnsConfig.copy(servers = currentServers))
    }

    private fun saveDnsConfig(config: CustomDnsConfig) {
        viewModelScope.launch {
            settingsRepository.setCustomDnsConfig(config)
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    // --- Routing Actions ---

    fun setRoutingDomainStrategy(strategy: String) {
        val updated = uiState.customRoutingConfig.copy(domainStrategy = strategy)
        saveRoutingConfig(updated)
    }

    fun openAddRoutingRuleDialog() {
        uiState = uiState.copy(
            isRoutingRuleDialogVisible = true,
            editingRoutingRule = null
        )
    }

    fun openEditRoutingRuleDialog(rule: RoutingRuleConfig) {
        uiState = uiState.copy(
            isRoutingRuleDialogVisible = true,
            editingRoutingRule = rule
        )
    }

    fun closeRoutingRuleDialog() {
        uiState = uiState.copy(
            isRoutingRuleDialogVisible = false,
            editingRoutingRule = null
        )
    }

    fun saveRoutingRule(rule: RoutingRuleConfig) {
        val currentRules = uiState.customRoutingConfig.rules.toMutableList()
        val editing = uiState.editingRoutingRule

        if (editing != null) {
            val idx = currentRules.indexOfFirst { it.id == editing.id }
            if (idx != -1) {
                currentRules[idx] = rule
            }
        } else {
            currentRules.add(rule)
        }

        saveRoutingConfig(uiState.customRoutingConfig.copy(rules = currentRules))
        closeRoutingRuleDialog()
    }

    fun toggleRoutingRule(id: String, enabled: Boolean) {
        val currentRules = uiState.customRoutingConfig.rules.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveRoutingConfig(uiState.customRoutingConfig.copy(rules = currentRules))
    }

    fun deleteRoutingRule(id: String) {
        val currentRules = uiState.customRoutingConfig.rules.filterNot { it.id == id }
        saveRoutingConfig(uiState.customRoutingConfig.copy(rules = currentRules))
    }

    fun moveRoutingRule(id: String, moveUp: Boolean) {
        val currentRules = uiState.customRoutingConfig.rules.toMutableList()
        val idx = currentRules.indexOfFirst { it.id == id }
        if (idx == -1) return

        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx in currentRules.indices) {
            val item = currentRules.removeAt(idx)
            currentRules.add(targetIdx, item)
            saveRoutingConfig(uiState.customRoutingConfig.copy(rules = currentRules))
        }
    }

    fun applyPresetTemplate(preset: RoutingPreset) {
        val newRule = preset.createRule().copy(id = UUID.randomUUID().toString())
        val currentRules = uiState.customRoutingConfig.rules.toMutableList()
        currentRules.add(newRule)
        saveRoutingConfig(uiState.customRoutingConfig.copy(rules = currentRules))
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetCustomRulesToDefault()
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }

    private fun saveRoutingConfig(config: CustomRoutingConfig) {
        viewModelScope.launch {
            settingsRepository.setCustomRoutingConfig(config)
            if (XrayVpnService.isServiceRunning) _events.emit(MainUiEvent.RestartVpn)
        }
    }
}
