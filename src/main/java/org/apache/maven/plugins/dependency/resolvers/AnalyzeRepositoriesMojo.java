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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.ArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.transfer.dependencies.collect.CollectorResult;
import org.apache.maven.shared.transfer.dependencies.collect.DependencyCollector;
import org.apache.maven.shared.transfer.dependencies.collect.DependencyCollectorException;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.transfer.dependencies.resolve.DependencyResolverException;

/**
 * Goal that resolves all project dependencies and then lists the repositories used by the build and by the transitive
 * dependencies
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.2
 */
@Mojo( name = "analyze-repositories", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class AnalyzeRepositoriesMojo
    extends AbstractDependencyMojo
{
//    @Parameter( defaultValue = "${session}", required = true, readonly = true )
//    private MavenSession session;

//    /**
//     * Contains the full list of projects in the reactor.
//    */
//    @Parameter( defaultValue = "${reactorProjects}", readonly = true, required = true )
//    private List<MavenProject> reactorProjects;

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

    @Component
    protected DependencyResolver dependencyResolver;

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

    private List<ArtifactRepository> originalFoundRepositories = new ArrayList<>();

    private HashMap<ResolvedRepository, Set<String>> foundRepositories = new HashMap<>();

    /**
     * Displays a list of the repositories used by this build.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
        throws MojoExecutionException
    {
        // Fetch mirrors from settings
        for ( ResolvedRepository resolvedRepository : getMirrors() )
        {
            addResolvedRepository( resolvedRepository, "Maven (user/global) settings" );
        }

        this.getLog().info( "======================================" );
        this.getLog().info( "Analyzing the dependencies:" );
        this.getLog().info( "======================================" );
        try
        {
            ProjectBuildingRequest buildingRequest =
                new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );

            buildingRequest.setProject( getProject() );
            buildingRequest.setRepositoryMerging( ProjectBuildingRequest.RepositoryMerging.POM_DOMINANT );

            Iterable<ArtifactResult> artifactResults =
                dependencyResolver.resolveDependencies( buildingRequest, getProject().getModel(), null );
            for ( ArtifactResult ar : artifactResults )
            {
                MavenProject project = getMavenProject( ArtifactUtils.key( ar.getArtifact() ) );
                processPom( ar.getArtifact(), project );
            }
        }
        catch ( DependencyResolverException exception )
        {
            throw new MojoExecutionException( "Cannot build project dependency ", exception );
        }
        this.getLog().info( "" );

        try
        {
            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();
            projectBuildingRequest.setResolveDependencies( false );
            CollectorResult collectResult =
                dependencyCollector.collectDependencies( projectBuildingRequest, getProject().getModel() );
            //this.getLog().info( "======================================" );
            //this.getLog().info( "Repositories used by this build (org):" );
            //this.getLog().info( "======================================" );
            for ( ArtifactRepository repo : collectResult.getRemoteRepositories() )
            {
                originalFoundRepositories.add( repo );
                // this.getLog().info( repo.toString() );
                // this.getLog().info( repositoryAsString( repo ) );
            }
//            for ( Artifact artifact : collectResult.getArtifacts() )
//            {
//                this.getLog().info( "--->>>> " + artifact.toString() );
//            }
            this.getLog().info( "" );
        }
        catch ( DependencyCollectorException e )
        {
            throw new MojoExecutionException( "Unable to resolve artifacts", e );
        }

        this.getLog().info( "======================================" );
        this.getLog().info( "Repositories used by this build (new):" );
        this.getLog().info( "======================================" );
//            for ( Map.Entry<ResolvedRepository, Set<String>> entry : foundRepositories.entrySet() )
//            {
//                this.getLog().info( "Found " + repoAsString( entry.getKey() )
//                    + "\n"
//                    + entry.getKey().toString() );
//                for ( String source : entry.getValue() )
//                {
//                    this.getLog().info( " @ " + source );
//                }
//            }
            // this.getLog().info( "**********" );
            for ( ArtifactRepository artifactRepository : originalFoundRepositories )
            {
                Set<String> sources = null;
                for ( Map.Entry<ResolvedRepository, Set<String>> entry : foundRepositories.entrySet() )
                {
                    if ( entry.getKey().getId().equals( artifactRepository.getId() )
                        && entry.getKey().getUrl().equals( artifactRepository.getUrl() ) )
                    {
                        sources = entry.getValue();
                        break;
                    }
                }
                this.getLog().info( artifactRepository.getId() + " (" + artifactRepository.getUrl() + ")" );
                if ( sources != null )
                {
                    for ( String source : sources )
                    {
                        this.getLog().info( " @ " + source );
                    }
                }
            }
    }

    private String repositoryAsString( ArtifactRepository repo )
    {
        StringBuilder buffer = new StringBuilder( 128 );
        buffer.append( repo.getId() );
        buffer.append( " (" ).append( repo.getUrl() );

//        // buffer.append( repo.getReleases() );
//        boolean r = repo.getReleases().isEnabled(), s = repo.getSnapshots().isEnabled();
//        if ( r && s )
//        {
//            buffer.append( ", releases+snapshots" );
//        }
//        else if ( r )
//        {
//            buffer.append( ", releases" );
//        }
//        else if ( s )
//        {
//            buffer.append( ", snapshots" );
//        }
//        else
//        {
//            buffer.append( ", disabled" );
//        }
        buffer.append( ")" );
        return buffer.toString();
    }

    /**
     * Parses the given String into GAV artifact coordinate information, adding the given type.
     *
     * @param artifactString should respect the format <code>groupId:artifactId[:version]</code>
     * @param type The extension for the artifact, must not be <code>null</code>.
     * @return the <code>Artifact</code> object for the <code>artifactString</code> parameter.
     * @throws MojoExecutionException if the <code>artifactString</code> doesn't respect the format.
     */
    protected ArtifactCoordinate getArtifactCoordinate( String artifactString, String type )
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

    protected ArtifactCoordinate getArtifactCoordinate( String groupId, String artifactId, String version, String type )
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
    protected MavenProject getMavenProject( String artifactString )
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

    private void processPom( Artifact artifact, MavenProject mavenProject )
        throws MojoExecutionException
    {
//        try
//        {
//            this.getLog().info( "Collect dependencies for project model " + mavenProject.getModel() );
//            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();
//            projectBuildingRequest.setProject( mavenProject );
//            projectBuildingRequest.setResolveDependencies( false );
//            projectBuildingRequest.setRepositoryMerging( ProjectBuildingRequest.RepositoryMerging.POM_DOMINANT );
//            DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();
//            coordinate.setGroupId( mavenProject.getModel().getGroupId() );
//            coordinate.setArtifactId( mavenProject.getModel().getArtifactId() );
//            coordinate.setVersion( mavenProject.getModel().getVersion() );
//            coordinate.setType( mavenProject.getModel().getPackaging() );
//            CollectorResult collectResult =
//                dependencyCollector.collectDependencies( projectBuildingRequest, coordinate );
//            this.getLog().info( "Repositories used by " +  coordinate );
//            for ( ArtifactRepository repo : collectResult.getRemoteRepositories() )
//            {
//                //this.getLog().info( repo.toString() );
//                this.getLog().info( " - " + repositoryAsString( repo ) );
//            }
//        }
//        catch ( DependencyCollectorException e )
//        {
//            this.getLog().warn( "Could not get remote repos for dep", e );
//        }

        if ( mavenProject != null )
        {
            for ( Repository r : mavenProject.getOriginalModel().getRepositories() )
            {
                addResolvedRepository( r, mavenProject.getModel().getPomFile().toString() );
                this.getLog().info( repoAsString( r )
                    + " @ " + mavenProject.getOriginalModel().getPomFile()
                    + " @ " + artifact );
            }
            for ( Repository r : mavenProject.getOriginalModel().getPluginRepositories() )
            {
                addResolvedRepository( r, mavenProject.getModel().getPomFile().toString() );
                this.getLog().info( repoAsString( r )
                        + " @ " + mavenProject.getOriginalModel().getPomFile()
                        + " @ " + artifact );
            }
            processParent( mavenProject );
        }
        else
        {
            this.getLog().warn( "mavenProject is null for " + artifact );
        }
    }

    private void processParent( MavenProject mavenProject )
        throws MojoExecutionException
    {
        MavenProject parent = mavenProject.getParent();
        if ( parent == null )
        {
            return;
        }

        Model originalModel = parent.getOriginalModel();
        if ( originalModel.getRepositories().size() != 0
            || originalModel.getPluginRepositories().size() != 0 )
        {
            String artifactKey = ArtifactUtils.key( parent.getGroupId(), parent.getArtifactId(), parent.getVersion() );
            MavenProject parentPom = getMavenProject( artifactKey );

            for ( Repository r : originalModel.getRepositories() )
            {
                addResolvedRepository( r, parentPom.getFile().toString() );
                this.getLog().info( repoAsString( r )
                        + " @ " + parentPom.getFile() + " (parent repo)" );
            }

            for ( Repository r : originalModel.getPluginRepositories() )
            {
                addResolvedRepository( r, parentPom.getFile().toString() );
                this.getLog().info( repoAsString( r )
                    + " @ " + parentPom.getFile() + " (parent plugin repo)" );
            }
        }

        processParent( parent );
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

    private void addResolvedRepository( Repository repository, String location )
    {
        ResolvedRepository resolvedRepository = new ResolvedRepository( repository );
        Set<String> locations = foundRepositories.get( resolvedRepository );
        if ( locations == null )
        {
            locations = new HashSet<>();
            locations.add( location );
            foundRepositories.put( resolvedRepository,  locations );
        }
        else
        {
            locations.add( location );
        }
    }

  private List<ResolvedRepository> getMirrors()
  {
      List<ResolvedRepository> resolvedRepositories = new ArrayList<>();
      for ( Mirror mirror : settings.getMirrors() )
      {
          ResolvedRepository resolvedRepository = new ResolvedRepository();
          resolvedRepository.setId( mirror.getId() );
          resolvedRepository.setUrl( mirror.getUrl() );
          resolvedRepository.setName( mirror.getName() );
          resolvedRepository.setLayout( mirror.getLayout() );
          resolvedRepositories.add( resolvedRepository );
      }
      return resolvedRepositories;
  }
}
