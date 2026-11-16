package io.madoka.magic.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 一位魔法少女的完整定义。
 *
 * <p><b>全部字段来自 JSON</b>（{@code data/madoka_magic/magical_girls/<id>.json}），
 * 这是一个纯数据类，代码里不再硬编码任何一位魔法少女。</p>
 */
public class MagicalGirl {
    public record WitchForm(double health, double attack, double armor, double speed,
                            List<String> skills, List<String> passives) {
        public WitchForm {
            skills = List.copyOf(skills);
            passives = List.copyOf(passives);
        }
    }

    public record Growth(double speedPerStep, double manaRegenPerStep, double armorPerStep) {
        public static final Growth NONE = new Growth(0, 0, 0);
    }

    /** 文案语气。 */
    public enum Tone {
        /** 决绝。 */
        DECISIVE,
        /** 轻佻。 */
        CASUAL,
        /** 压抑。 */
        PRESSED
    }

    private final String id;
    private final String name;
    private final String translationKey;
    private final String classification;
    private final String role;
    private final int primaryColor;
    private final int secondaryColor;
    private final String element;
    private final String school;
    private final int manaMax;
    private final Tone tone;
    private final double damageDealt;
    private final double damageTaken;
    private final double taintRate;
    private final double manaGain;
    private final ResourceLocation contractMaterial;
    private final String witchDimension;
    private final List<String> skills;
    private final List<String> passives;
    private final WitchForm witchForm;
    private final Growth growth;

    public MagicalGirl(String id, String name, String translationKey, String classification, String role,
                      int primaryColor, int secondaryColor, String element, String school,
                      int manaMax, Tone tone,
                      double damageDealt, double damageTaken, double taintRate, double manaGain,
                      ResourceLocation contractMaterial, String witchDimension,
                      List<String> skills, List<String> passives, WitchForm witchForm, Growth growth) {
        this.id = id;
        this.name = name;
        this.translationKey = translationKey;
        this.classification = classification;
        this.role = role;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.element = element;
        this.school = school;
        this.manaMax = manaMax;
        this.tone = tone;
        this.damageDealt = damageDealt;
        this.damageTaken = damageTaken;
        this.taintRate = taintRate;
        this.manaGain = manaGain;
        this.contractMaterial = contractMaterial;
        this.witchDimension = witchDimension;
        this.skills = skills == null ? List.of() : List.copyOf(skills);
        this.passives = passives == null ? List.of() : List.copyOf(passives);
        this.witchForm = witchForm;
        this.growth = growth;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getClassification() {
        return classification;
    }

    public String getRole() {
        return role;
    }

    public int getPrimaryColor() {
        return primaryColor;
    }

    public int getSecondaryColor() {
        return secondaryColor;
    }

    public String getElement() {
        return element;
    }

    public String getSchool() {
        return school;
    }

    public int getManaMax() {
        return manaMax;
    }

    public Tone getTone() {
        return tone;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public double getTaintRate() {
        return taintRate;
    }

    public double getManaGain() {
        return manaGain;
    }

    public ResourceLocation getContractMaterial() {
        return contractMaterial;
    }

    public String getWitchDimension() {
        return witchDimension;
    }

    /** 该魔法少女的主动技能 id。 */
    public List<String> getSkills() {
        return skills;
    }

    /** 该魔法少女的被动技能 id。 */
    public List<String> getPassives() {
        return passives;
    }

    public WitchForm getWitchForm() {
        return witchForm;
    }

    public Growth getGrowth() {
        return growth;
    }

    public List<String> getSkills(boolean witch) {
        return witch && witchForm != null ? witchForm.skills() : skills;
    }

    public List<String> getPassives(boolean witch) {
        return witch && witchForm != null ? witchForm.passives() : passives;
    }

    /** 显示名：优先用翻译键，没有翻译时回退到 JSON 里的 name。 */
    public String getDisplayName() {
        String translated = Component.translatable(translationKey).getString();
        return translated.isBlank() || translated.equals(translationKey) ? name : translated;
    }

    public float taintRateFloat() {
        return (float) Math.max(0.0D, Math.min(10.0D, taintRate));
    }
}
