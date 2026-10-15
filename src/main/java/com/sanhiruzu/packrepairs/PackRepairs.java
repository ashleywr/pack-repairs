package com.sanhiruzu.packrepairs;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;

@Mod("packrepairs")
public final class PackRepairs {
    public PackRepairs(net.neoforged.bus.api.IEventBus modBus) {
        RepairRuntime.initialize();
        modBus.addListener(BuddycardsRulebookPack::addPack);
        modBus.addListener(LegacyRegistryPack::addPack);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BloomingNatureRepair::aboutToStart);
    }
}
