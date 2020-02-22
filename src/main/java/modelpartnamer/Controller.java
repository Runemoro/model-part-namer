package modelpartnamer;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.EntityType;
import net.minecraft.text.LiteralText;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Controller {
    private static final List<PartInfo> parts = new ArrayList<>();
    private static final Object2IntFunction<String> partCounts = new Object2IntOpenHashMap<>();
    private static int index = 0;
    public static ModelPart selectedModelPart;

    static {
        init();
    }

    @SuppressWarnings({"unchecked", "JavaReflectionMemberAccess"})
    private static void init() {
        try {
            Field renderersField;

            try {
                renderersField = EntityRenderDispatcher.class.getDeclaredField("renderers");
            } catch (NoSuchFieldException e) {
                renderersField = EntityRenderDispatcher.class.getDeclaredField("field_4696");
            }

            renderersField.setAccessible(true);
            Map<EntityType<?>, EntityRenderer<?>> renderers = (Map<EntityType<?>, EntityRenderer<?>>) renderersField.get(MinecraftClient.getInstance().getEntityRenderManager());
            List<Map.Entry<EntityType<?>, EntityRenderer<?>>> sortedRenderers = renderers
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing(e -> EntityType.getId(e.getKey())))
                    .collect(Collectors.toList());

            for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : sortedRenderers) {
                String id = EntityType.getId(entry.getKey()).toString();
                EntityRenderer<?> renderer = entry.getValue();

                if (renderer instanceof LivingEntityRenderer) {
                    EntityModel model = ((LivingEntityRenderer) renderer).getModel();
                    addParts(id, model);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addParts(String id, EntityModel model) throws ReflectiveOperationException {
        int i = 0;

        for (Field field : model.getClass().getDeclaredFields()) {
            if (ModelPart.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                String fieldName = field.getName();
                ModelPart part = (ModelPart) field.get(model);
                parts.add(new PartInfo(id, fieldName, i++, part));
            }
        }

        partCounts.put(id, i);
    }

    public static void next() {
        index = Math.floorMod(index + 1, parts.size());
        update();
    }

    public static void last() {
        index = Math.floorMod(index - 1, parts.size());
        update();
    }

    public static void nextUnmapped() {
        findUnmapped(1);
    }

    public static void lastUnmapped() {
        findUnmapped(-1);
    }

    private static void findUnmapped(int direction) {
        int startIndex = index;

        while (true) {
            index = Math.floorMod(index + direction, parts.size());

            if (parts.get(index).mappedName == null) {
                update();
                return;
            }

            if (index == startIndex) {
                message("All mapped!");
                return;
            }
        }
    }

    private static void update() {
        PartInfo partInfo = parts.get(index);
        selectedModelPart = partInfo.part;
        message("[" + (index + 1) + "/" + parts.size() + "] Part " + (partInfo.index + 1) + "/" + partCounts.getInt(partInfo.id) + " of model for " + partInfo.id);

        if (partInfo.mappedName != null) {
            message("Already mapped as '" + partInfo.mappedName + "'");
        }
    }

    public static void command(String s) {
        if (s.startsWith("/")) {
            return;
        }

        String[] parts = s.split(" ");

        if (parts[0].equals("u")) {
            Controller.parts.get(index).mappedName = null;
            return;
        }

        if (parts[0].equals("n")) {
            next();
            return;
        }

        if (parts[0].equals("l")) {
            last();
            return;
        }

        if (parts[0].equals("j")) {
            jump(Integer.parseInt(parts[1]));
            return;
        }

        if (parts[0].equals("nu")) {
            nextUnmapped();
            return;
        }

        if (parts[0].equals("lu")) {
            lastUnmapped();
            return;
        }

        if (parts[0].equals("load")) {
            loadMappings(s.substring(5));
            return;
        }

        if (parts[0].equals("save")) {
            saveMappings(s.substring(5));
            return;
        }


//        StringBuilder sb = new StringBuilder();
//
//        boolean lastWasSpace = false;
//        for (char c : s.toCharArray()) {
//            if (c == ' ') {
//                lastWasSpace = true;
//            }
//
//            sb.append(lastWasSpace ? Character.toUpperCase(c) : c);
//            lastWasSpace = false;
//        }
//
//        Controller.parts.get(index).mappedName = sb.toString();

        Controller.parts.get(index).mappedName = s.toUpperCase().replace(' ', '_');
        next();
    }

    private static void jump(int index) {
        Controller.index = Math.floorMod(index - 1, parts.size());
        update();
    }

    private static void message(String message) {
        MinecraftClient.getInstance().player.sendMessage(new LiteralText(message));
    }

    private static void loadMappings(String s) {
        Map<String, String> mappings = new HashMap<>();

        try {
            for (String line : Files.readAllLines(Paths.get(s))) {
                String[] split = line.split(" ");
                mappings.put(split[0], split[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (PartInfo part : parts) {
            part.mappedName = mappings.get(part.fieldName);
        }

        message("Successfully loaded");
    }

    private static void saveMappings(String s) {
        try (Writer w = new FileWriter(s)) {
            for (PartInfo part : parts) {
                if (part.mappedName != null) {
                    w.append(part.fieldName).append(" ").append(part.mappedName).append("\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        message("Sucessfully saved");
    }

    private static class PartInfo {
        private final String id;
        private final String fieldName;
        private final int index;
        private final ModelPart part;
        private String mappedName;

        private PartInfo(String id, String fieldName, int index, ModelPart part) {
            this.id = id;
            this.fieldName = fieldName;
            this.index = index;
            this.part = part;
        }
    }
}
