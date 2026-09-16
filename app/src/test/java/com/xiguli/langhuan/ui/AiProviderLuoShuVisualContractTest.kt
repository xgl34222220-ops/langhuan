package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderLuoShuVisualContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun providerSetupUsesLuoShuSurfacesAndKeepsConnectionFlow() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/AiProviderSetupPage.kt")

        assertTrue(source.contains("LanghuanIconButton"))
        assertTrue(source.contains("LanghuanCard"))
        assertTrue(source.contains("LanghuanBadge"))
        assertFalse(source.contains("OutlinedTextField("))
        assertFalse(source.contains("HorizontalDivider("))

        assertTrue(source.contains("vm::setProviderName"))
        assertTrue(source.contains("vm::setBaseUrl"))
        assertTrue(source.contains("vm::setApiKey"))
        assertTrue(source.contains("vm::detectProvider"))
        assertTrue(source.contains("vm.selectModel(model)"))
        assertTrue(source.contains("vm::setManualModel"))
        assertTrue(source.contains("vm.saveProvider()"))
        assertTrue(source.contains("vm.activateProvider(provider.id)"))
        assertTrue(source.contains("vm.editProvider(provider.id)"))
        assertTrue(source.contains("vm.deleteProvider(provider.id)"))
        assertTrue(source.contains("vm.newProvider()"))
        assertTrue(source.contains("TaskModelRoutingPanel(taskRoutingVm)"))
        assertTrue(source.contains("ProviderQuickSwitchSheet("))
    }

    @Test
    fun quickSwitchUsesLuoShuSheetAndKeepsRealModelDiscovery() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/ProviderQuickSwitch.kt")

        assertTrue(source.contains("LocalLanghuanUiTokens"))
        assertTrue(source.contains("LanghuanCard"))
        assertTrue(source.contains("LanghuanIconButton"))
        assertFalse(source.contains("FilterChip("))

        assertTrue(source.contains("ProviderAutoDetector"))
        assertTrue(source.contains("detector.detect(provider.baseUrl, key)"))
        assertTrue(source.contains("routingStore.rememberDiscovery"))
        assertTrue(source.contains("repository.saveProvider("))
        assertTrue(source.contains("makeDefault = true"))
        assertTrue(source.contains("viewModel::refreshModels"))
        assertTrue(source.contains("viewModel.switchModel(model.id)"))
        assertTrue(source.contains("modelRoute(provider.baseUrl, model.id)"))
    }
}
