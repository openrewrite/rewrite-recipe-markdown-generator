package org.openrewrite;

import org.openrewrite.config.Environment;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.internal.StringUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.util.stream.Collectors.toSet;

@Command(name = "rewrite-recipe-markdown-generator", mixinStandardHelpOptions = true,
        description = "Generates documentation for OpenRewrite recipes in markdown format",
        version = "1.0.0-SNAPSHOT")
class RecipeMarkdownGenerator implements Runnable {

    @Parameters(index = "0", description = "Destination directory for generated recipe markdown")
    private String destinationDirectoryName;

    private String getRecipeCategory(RecipeDescriptor recipe) {
        String recipePath = getRecipePath(recipe);
        return recipePath.substring(0, recipePath.lastIndexOf("/"));
    }

    private String getRecipePath(RecipeDescriptor recipe) {
        String name = recipe.getName();
        if (name.startsWith("org.openrewrite")) {
            return name.substring(16).replaceAll("\\.", "/").toLowerCase();
        } else {
            throw new RuntimeException("Recipe package unrecognized: " + name);
        }
    }

    private Path getRecipePath(Path recipesPath, RecipeDescriptor recipeDescriptor) {
        return recipesPath.resolve(getRecipePath(recipeDescriptor) + ".md");
    }

    private String getRecipeRelativePath(RecipeDescriptor recipe) {
        return "reference/recipes/" + getRecipePath(recipe) + ".md";
    }

    @Override
    public void run() {
        Path outputPath = Paths.get(destinationDirectoryName);
        Path recipesPath = outputPath.resolve("reference/recipes");
        try {
            Files.createDirectories(recipesPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Environment env = Environment.builder()
                .scanRuntimeClasspath()
                .build();
        List<RecipeDescriptor> recipeDescriptors = new ArrayList<>(env.listRecipeDescriptors());
        SortedMap<String, List<RecipeDescriptor>> groupedRecipes = new TreeMap<>();
        for (RecipeDescriptor recipe : recipeDescriptors) {
            String recipeCategory = getRecipeCategory(recipe);
            List<RecipeDescriptor> categoryRecipes = groupedRecipes.computeIfAbsent(recipeCategory, k -> new ArrayList<>());
            categoryRecipes.add(recipe);
        }
        // insert missing parent categories
        Map<String, List<RecipeDescriptor>> missingCategories = new HashMap<>();
        for (String category : groupedRecipes.keySet()) {
            String p = category;
            while (!p.isEmpty()) {
                if (!groupedRecipes.containsKey(p)) {
                    missingCategories.put(p, Collections.emptyList());
                }
                if (p.contains("/")) {
                    p = p.substring(0, p.lastIndexOf("/"));
                } else {
                    p = "";
                }
            }
        }
        groupedRecipes.putAll(missingCategories);
        for (RecipeDescriptor recipeDescriptor : recipeDescriptors) {
            writeRecipe(recipeDescriptor, recipesPath);
        }
        Path summarySnippetPath = outputPath.resolve("SUMMARY_snippet.md");
        BufferedWriter summarySnippetWriter;
        try {
            summarySnippetWriter = Files.newBufferedWriter(summarySnippetPath, StandardOpenOption.CREATE);
            summarySnippetWriter.write("* Recipes");
            summarySnippetWriter.newLine();
            for (Map.Entry<String, List<RecipeDescriptor>> category : groupedRecipes.entrySet()) {
                writeSnippet(summarySnippetWriter, category);
                // get direct subcategory descendants
                Set<String> subcategories = groupedRecipes.keySet().stream().filter(k ->
                    !k.equals(category.getKey()) && k.startsWith(category.getKey())
                )
                        .map(k -> k.substring(category.getKey().length() + 1))
                        .filter(k -> !k.contains("/"))
                        .collect(toSet());
                writeCategoryIndex(outputPath, category, subcategories);
            }
            summarySnippetWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeCategoryIndex(Path outputPath, Map.Entry<String, List<RecipeDescriptor>> categoryEntry, Set<String> subcategories) throws IOException {
        String category = categoryEntry.getKey();
        Path categoryIndexPath = outputPath.resolve("reference/recipes/" + category + "/README.md");
        BufferedWriter categoryIndexWriter = Files.newBufferedWriter(categoryIndexPath, StandardOpenOption.CREATE);
        String categoryName;
        if (category.contains("/")) {
            categoryName = StringUtils.capitalize(category.substring(category.lastIndexOf("/") + 1));
        } else {
            categoryName = StringUtils.capitalize(category);
        }
        categoryIndexWriter.write("# " + categoryName);
        categoryIndexWriter.newLine();
        if (!categoryEntry.getValue().isEmpty()) {
            categoryIndexWriter.newLine();
            categoryIndexWriter.write("### Recipes");
            categoryIndexWriter.newLine();
            for (RecipeDescriptor recipe : categoryEntry.getValue()) {
                String recipePath = getRecipePath(recipe);
                categoryIndexWriter.write("* [" + recipe.getDisplayName() + "](" + recipePath.substring(recipePath.lastIndexOf("/") + 1) + ".md)");
                categoryIndexWriter.newLine();
            }
        }
        if (!subcategories.isEmpty()) {
            categoryIndexWriter.newLine();
            categoryIndexWriter.write("### Subcategories");
            categoryIndexWriter.newLine();
            for (String subcategory : subcategories) {
                categoryIndexWriter.write("* [" + StringUtils.capitalize(subcategory) + "](" + subcategory + "/README.md)");
                categoryIndexWriter.newLine();
            }
        }
        categoryIndexWriter.close();
    }

    private void writeSnippet(BufferedWriter summarySnippetWriter, Map.Entry<String, List<RecipeDescriptor>> categoryEntry) throws IOException {
        StringBuilder indentBuilder = new StringBuilder("  ");
        String category = categoryEntry.getKey();
        long levels = category.chars().filter(ch -> ch == '/').count();
        for (int i = 0; i < levels; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();
        StringBuilder categoryBuilder = new StringBuilder(indent).append("* [");
        if (category.contains("/")) {
            categoryBuilder.append(StringUtils.capitalize(category.substring(category.lastIndexOf("/") + 1)));
        } else {
            categoryBuilder.append(StringUtils.capitalize(category));
        }
        categoryBuilder.append("](reference/recipes/").append(category).append("/README.md)");
        summarySnippetWriter.write(categoryBuilder.toString());
        summarySnippetWriter.newLine();
        for (RecipeDescriptor recipe : categoryEntry.getValue()) {
            summarySnippetWriter.write(indent + "  * [" + recipe.getDisplayName() + "](" + getRecipeRelativePath(recipe) + ")");
            summarySnippetWriter.newLine();
        }
    }

    private void writeRecipe(RecipeDescriptor recipeDescriptor, Path outputPath) {
        Path recipeMarkdownPath = getRecipePath(outputPath, recipeDescriptor);
        try {
            Files.createDirectories(recipeMarkdownPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(recipeMarkdownPath, StandardOpenOption.CREATE);
            writer.write("# " + recipeDescriptor.getDisplayName());
            writer.newLine();
            writer.newLine();
            writer.write("**" + recipeDescriptor.getName().replaceAll("_", "\\\\_") + "**  ");
            writer.newLine();
            if (!StringUtils.isNullOrEmpty(recipeDescriptor.getDescription())) {
                writer.write("_" + recipeDescriptor.getDescription() + "_");
                writer.newLine();
            }
            writer.newLine();
            if (!recipeDescriptor.getTags().isEmpty()) {
                writer.write("### Tags");
                writer.newLine();
                writer.newLine();
                for (String tag : recipeDescriptor.getTags()) {
                    writer.write("* " + tag);
                    writer.newLine();
                }
                writer.newLine();
            }
            if (!recipeDescriptor.getOptions().isEmpty()) {
                writer.write("### Options");
                writer.newLine();
                writer.newLine();
                for (OptionDescriptor option : recipeDescriptor.getOptions()) {
                    StringBuilder optionBuilder = new StringBuilder("* ")
                            .append(option.getName())
                            .append(": ")
                            .append(option.getType());
                    if (option.isRequired()) {
                        optionBuilder.append('!');
                    }
                    writer.write(optionBuilder.toString());
                    writer.newLine();
                    if (!StringUtils.isNullOrEmpty(option.getDescription())) {
                        writer.write("  * " + option.getDescription());
                        writer.newLine();
                    }
                }
                writer.newLine();
            }
            if (!recipeDescriptor.getRecipeList().isEmpty()) {
                writer.write("## Recipe list");
                writer.newLine();
                writer.newLine();
                long recipeDepth = getRecipePath(recipeDescriptor).chars().filter(ch -> ch == '/').count();
                StringBuilder pathToRecipesBuilder = new StringBuilder();
                for (int i = 0; i < recipeDepth; i++) {
                    pathToRecipesBuilder.append("../");
                }
                String pathToRecipes = pathToRecipesBuilder.toString();
                for (RecipeDescriptor recipe : recipeDescriptor.getRecipeList()) {
                    writer.write("* [" + recipe.getDisplayName() + "](" + pathToRecipes + getRecipePath(recipe) + ".md)");
                    writer.newLine();
                    if (!recipe.getOptions().isEmpty()) {
                        for (OptionDescriptor option : recipe.getOptions()) {
                            if (option.getValue() != null) {
                                writer.write("  * " + option.getName() + ": " + option.getValue().toString());
                                writer.newLine();
                            }
                        }

                    }
                }
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new RecipeMarkdownGenerator()).execute(args);
        System.exit(exitCode);
    }
}
