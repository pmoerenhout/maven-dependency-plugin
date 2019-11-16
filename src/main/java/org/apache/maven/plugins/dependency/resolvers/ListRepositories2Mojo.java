package org.apache.maven.plugins.dependency.resolvers;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.dependencies.collect.DependencyCollector;
import org.apache.maven.shared.transfer.dependencies.collect.DependencyCollectorException;
import org.apache.maven.shared.transfer.dependencies.collect.DependencyCollectorNode;
import org.apache.maven.shared.transfer.dependencies.collect.traversal.DependencyCollectorNodeVisitor;

/**
 * Goal that resolves all project dependencies and then lists the repositories used by the build and by the transitive
 * dependencies
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.2
 */
@Mojo( name = "list-repositories-2", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class ListRepositories2Mojo
    extends AbstractDependencyMojo
{
    /**
     * Maven Project Builder component.
    */
    @Component
    protected ProjectBuilder projectBuilder;

    /**
     * Dependency collector, needed to resolve dependencies.
     */
    @Component( role = DependencyCollector.class )
    private DependencyCollector dependencyCollector;

    /**
     * Component used to resolve artifacts and download their files from remote repositories.
     */
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * The system settings for Maven. This is the instance resulting from
     * merging global and user-level settings files.
     */
    @Parameter( defaultValue = "${settings}", readonly = true, required = true )
    private Settings settings;

    /**
     * Remote repositories used for the project.
     */
    @Parameter( defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true )
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * Include parent poms in the dependency resolution list.
     *
     * @since 2.8
     */
    @Parameter( property = "includeParents", defaultValue = "false" )
    boolean includeParents;

    /**
     * Displays a list of the repositories used by this build.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {
        final Set<ArtifactRepository> repositories = new HashSet<ArtifactRepository>();

        DependencyCollectorNodeVisitor visitor = new DependencyCollectorNodeVisitor()
        {
            @Override
            public boolean visit( DependencyCollectorNode dependencyCollectorNode )
            {
                System.out.println( "visit artifact " + dependencyCollectorNode.getArtifact() );
                for ( ArtifactRepository artifactRepository: dependencyCollectorNode.getRemoteRepositories() )
                {
                    System.out.println( " remote repos: " + artifactRepository.getId()
                        + " " + artifactRepository.getUrl() );
                }
                System.out.println( " scope: " + dependencyCollectorNode.getScope() );
                System.out.println( " optional: " + dependencyCollectorNode.getOptional() );
                for ( DependencyCollectorNode child : dependencyCollectorNode.getChildren() )
                {
                    System.out.println( " child: " + child.getArtifact() );
                }

                repositories.addAll( dependencyCollectorNode.getRemoteRepositories() );

                return true;
            }

            @Override
            public boolean endVisit( DependencyCollectorNode dependencyCollectorNode )
            {
                return true;
            }
        };

        try
        {
            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();

            dependencyCollector.visit( projectBuildingRequest, getProject().getModel(), visitor );

            this.getLog().info( "Repositories used by this build:" );
            for ( ArtifactRepository repo : repositories )
            {
                this.getLog().info( repo.toString() );
            }
        }
        catch ( DependencyCollectorException e )
        {
            throw new MojoExecutionException( "Unable to collect artifacts", e );
        }
    }

    private void listRepository( ArtifactRepository artifactRepository, Set<String> locations )
    {
        StringBuilder sb = new StringBuilder( 256 );
        sb.append( artifactRepository.toString() );
        for ( String location : locations )
        {
            sb.append( "   locate: " ).append( location ).append( "\n" );
        }
        this.getLog().info( sb.toString() );
    }

    /**
     * Parses the given String into GAV artifact coordinate information, adding the given type.
     *
     * @param artifactString should respect the format <code>groupId:artifactId[:version]</code>
     * @param type The extension for the artifact, must not be <code>null</code>.
     * @return the <code>Artifact</code> object for the <code>artifactString</code> parameter.
     * @throws MojoExecutionException if the <code>artifactString</code> doesn't respect the format.
     */
    private ArtifactCoordinate getArtifactCoordinate( String artifactString, String type )
        throws MojoExecutionException
    {
        if ( org.codehaus.plexus.util.StringUtils.isEmpty( artifactString ) )
        {
            throw new IllegalArgumentException( "artifact parameter could not be empty" );
        }

        String groupId; // required
        String artifactId; // required
        String version; // optional

        String[] artifactParts = artifactString.split( ":" );
        switch ( artifactParts.length )
        {
            case 2:
                groupId = artifactParts[0];
                artifactId = artifactParts[1];
                version = Artifact.LATEST_VERSION;
                break;
            case 3:
                groupId = artifactParts[0];
                artifactId = artifactParts[1];
                version = artifactParts[2];
                break;
            default:
                throw new MojoExecutionException( "The artifact parameter '" + artifactString
                    + "' should be conform to: " + "'groupId:artifactId[:version]'." );
        }
        return getArtifactCoordinate( groupId, artifactId, version, type );
    }

    private ArtifactCoordinate getArtifactCoordinate( String groupId, String artifactId, String version, String type )
    {
        DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId( groupId );
        coordinate.setArtifactId( artifactId );
        coordinate.setVersion( version );
        coordinate.setExtension( type );
        return coordinate;
    }

    /**
     * Retrieves the Maven Project associated with the given artifact String, in the form of
     * <code>groupId:artifactId[:version]</code>. This resolves the POM artifact at those coordinates and then builds
     * the Maven project from it.
     *
     * @param artifactString Coordinates of the Maven project to get.
     * @return New Maven project.
     * @throws MojoExecutionException If there was an error while getting the Maven project.
     */
    private MavenProject getMavenProject( String artifactString )
        throws MojoExecutionException
    {
        ArtifactCoordinate coordinate = getArtifactCoordinate( artifactString, "pom" );
        try
        {
            ProjectBuildingRequest pbr = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            pbr.setRemoteRepositories( remoteRepositories );
            pbr.setProject( null );
            pbr.setValidationLevel( ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL );
            pbr.setResolveDependencies( false );
            Artifact artifact = artifactResolver.resolveArtifact( pbr, coordinate ).getArtifact();
            return projectBuilder.build( artifact.getFile(), pbr ).getProject();
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to get the POM for the artifact '" + artifactString
                + "'. Verify the artifact parameter.", e );
        }
    }

    private void traversePom( ArtifactRepository artifactRepository,
                              Artifact artifact, MavenProject mavenProject, Set<String> locations )
        throws MojoExecutionException
    {
        if ( mavenProject != null )
        {
            for ( Repository repository : mavenProject.getOriginalModel().getRepositories() )
            {
                getLog().info( "1: " + artifact + repository.getId() + " - " + artifactRepository.getId() );
                if ( repository.getId().equals( artifactRepository.getId() ) )
                {
                    locations.add( mavenProject.getModel().getPomFile().toString() );
                    // addCollectedRepository( r, mavenProject.getModel().getPomFile().toString() );
                    this.getLog().debug( "Repository: " + repoAsString( repository )
                        + " @ " + mavenProject.getOriginalModel().getPomFile()
                        + " @ " + artifact );
                }
            }
            for ( Repository pluginRepository : mavenProject.getOriginalModel().getPluginRepositories() )
            {
                getLog().info( "2: " + artifact + pluginRepository.getId() + " - " + artifactRepository.getId() );
                if ( pluginRepository.getId().equals( artifactRepository.getId() ) )
                {
                    locations.add( mavenProject.getModel().getPomFile().toString() );
                    // addCollectedRepository( pluginRepository, mavenProject.getModel().getPomFile().toString() );
                    this.getLog().debug( "Plugin repository: " + repoAsString( pluginRepository )
                        + " @ " + mavenProject.getOriginalModel().getPomFile()
                        + " @ " + artifact );
                }
            }
            traverseParentPom( artifactRepository, mavenProject, locations );
        }
        else
        {
            throw new MojoExecutionException( "No POM for the artifact '" + artifact + "'" );
        }
        return;
    }

    private void traverseParentPom( ArtifactRepository artifactRepository,
                                    MavenProject mavenProject, Set<String> locations )
        throws MojoExecutionException
    {
        MavenProject parent = mavenProject.getParent();
        if ( parent != null )
        {
            Model originalModel = parent.getOriginalModel();
            if ( originalModel.getRepositories().size() != 0
                || originalModel.getPluginRepositories().size() != 0 )
            {
                String artifactKey =
                    ArtifactUtils.key( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );
                MavenProject parentPom = getMavenProject( artifactKey );

                for ( Repository repository : originalModel.getRepositories() )
                {
                    getLog().info( "3: " + parent + repository.getId() + " - " + artifactRepository.getId() );
                    if ( repository.getId().equals( artifactRepository.getId() ) )
                    {
                        locations.add( parentPom.getFile().toString() );
                        // addCollectedRepository(repository, parentPom.getFile().toString());
                        this.getLog().debug( "Parent repository: " + repoAsString( repository )
                            + " @ " + parentPom.getFile() );
                    }
                }
                for ( Repository pluginRepository : originalModel.getPluginRepositories() )
                {
                    getLog().info( "4: " + parent + pluginRepository.getId() + " - " + artifactRepository.getId() );
                    if ( pluginRepository.getId().equals( artifactRepository.getId() ) )
                    {
                        locations.add( parentPom.getFile().toString() );
                        // addCollectedRepository(pluginRepository, parentPom.getFile().toString());
                        this.getLog().debug( "Parent plugin repository: " + repoAsString( pluginRepository )
                            + " @ " + parentPom.getFile() );
                    }
                }
            }
            traverseParentPom( artifactRepository, parent, locations );
        }
        return;
    }

    private String repoAsString( Repository repository )
    {
        StringBuilder sb = new StringBuilder( 32 );
        sb.append( repository.getId() );
        sb.append( " (" );
        sb.append( repository.getUrl() );
        sb.append( ")" );
        return sb.toString();
    }

}
