import org.oryxel.viabedrockutility.material.VanillaMaterials;

public class MaterialParserTest {
    public static void main(String[] args) {
        VanillaMaterials.init();

        VanillaMaterials.NAME_TO_MATERIAL.forEach((k, v) -> System.out.println(v.identifier() + ":" + v.info()));
    }
}
