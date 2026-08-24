package ru.white.module.api;


import ru.white.utils.animation.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Category {
    COMBAT("Combat","B"),
    MOVEMENT("Movement","H"),
    PLAYER("Player","N"),
    OTHER("Misc","L"),
    RENDER("Render","Q");
    private final String name;
    private final String icon;



    public ru.white.utils.animation.satoshi.Animation alphaS = new EaseInOutQuad(300,1);
    public ru.white.utils.animation.satoshi.Animation alphaS2 = new EaseInOutQuad(300,1);


    public Animation animation = new Animation();

}