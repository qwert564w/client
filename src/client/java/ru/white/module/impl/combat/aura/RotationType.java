package ru.white.module.impl.combat.aura;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.white.module.impl.combat.aura.rotation.*;

@Getter
@RequiredArgsConstructor
public enum RotationType {

    FUNTIME("FunTime_Legacy", new FunTimeRotation()),
    SPOOKYTIME("SpookyTime", new SpookyTimeRotation()),
    MATRIX("Default", new MatrixRotation()),
    SNAP("Snap", new SnapRotation()),
    NEURO("Neuro", new NeuroRotation()),
    HVH("HvH", new HvHRotation()),
    SPOOKY("SpookyTime 1.21", new MatrixRotation());


    private final String name;
    private final RotationAura rotation;

}
