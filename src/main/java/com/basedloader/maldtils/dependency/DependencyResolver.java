package com.basedloader.maldtils.dependency;

import com.basedloader.maldtils.logger.Logger;

import java.nio.file.Path;
import java.util.List;

/**
 * Class used mostly to get dependencies for things such as minecraft. The reason this isn't just one class is for example in the future when a gradle plugin may want to use its cache in order to get libraries.
 */
public interface DependencyResolver {

    Path resolve(MavenDependency dependency, Logger logger);

    List<Path> resolveVanillaDependencies(Logger logger);
}
