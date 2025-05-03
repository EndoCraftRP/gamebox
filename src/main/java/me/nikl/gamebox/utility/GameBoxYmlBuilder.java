package me.nikl.gamebox.utility;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;
import org.yaml.snakeyaml.representer.Representer;
import me.nikl.gamebox.module.local.LocalModuleData;
import org.yaml.snakeyaml.Yaml;

public class GameBoxYmlBuilder {
    public static Yaml buildLocalModuleDataYml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        CustomClassLoaderConstructor constructor = new CustomClassLoaderConstructor(LocalModuleData.class, LocalModuleData.class.getClassLoader(), loaderOptions);
        DumperOptions dumperOpts = new DumperOptions();
        Representer representer = new Representer(dumperOpts);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        return new Yaml(constructor, representer);
    }
}