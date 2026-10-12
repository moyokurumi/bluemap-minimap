import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Rasterizes this project's rect/polygon-only SVG with the build JDK (no extra tools). */
class RenderIcon {
    public static void main(String[] args) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(Path.of("art/icon.svg").toFile());
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        var shapes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < shapes.getLength(); i++) {
            if (!(shapes.item(i) instanceof Element shape)) continue;
            graphics.setColor(Color.decode(shape.getAttribute("fill")));
            if (shape.getTagName().equals("rect")) {
                graphics.fillRect(number(shape, "x"), number(shape, "y"),
                        number(shape, "width"), number(shape, "height"));
            } else if (shape.getTagName().equals("path")) {
                var tokens = java.util.regex.Pattern.compile("[MLHVZ]|[0-9]+")
                        .matcher(shape.getAttribute("d")).results().map(result -> result.group()).toList();
                var path = new Path2D.Double();
                double x = 0, y = 0;
                for (int t = 0; t < tokens.size(); t++) {
                    switch (tokens.get(t)) {
                        case "M", "L" -> {
                            boolean move = tokens.get(t).equals("M");
                            x = Double.parseDouble(tokens.get(++t));
                            y = Double.parseDouble(tokens.get(++t));
                            if (move) path.moveTo(x, y); else path.lineTo(x, y);
                        }
                        case "H" -> { x = Double.parseDouble(tokens.get(++t)); path.lineTo(x, y); }
                        case "V" -> { y = Double.parseDouble(tokens.get(++t)); path.lineTo(x, y); }
                        case "Z" -> path.closePath();
                        default -> throw new IllegalArgumentException("Unsupported SVG path");
                    }
                }
                graphics.fill(path);
            } else throw new IllegalArgumentException("Unsupported SVG shape");
        }
        graphics.dispose();
        Path output = Path.of("src/main/resources/assets/bluemap_minimap/icon.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(image, "png", output.toFile());
    }

    private static int number(Element element, String attribute) {
        return element.hasAttribute(attribute) ? Integer.parseInt(element.getAttribute(attribute)) : 0;
    }
}
