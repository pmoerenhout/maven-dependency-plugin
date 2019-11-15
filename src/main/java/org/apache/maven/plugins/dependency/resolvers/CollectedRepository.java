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

import java.util.Objects;

import org.apache.maven.model.Repository;

/**
 * A repository contains the information needed for establishing
 * connections with
 *         remote repository.
 *
 * @version $Revision$ $Date$
 */
public class CollectedRepository extends Repository
{

  public CollectedRepository()
  {
  }

  public CollectedRepository( Repository repository )
  {
    super.setId( repository.getId() );
    super.setName( repository.getName() );
    super.setUrl( repository.getUrl() );
    super.setLayout( repository.getLayout() );
    if ( repository.getReleases() != null )
    {
      super.setReleases( repository.getReleases().clone() );
    }
    if ( repository.getSnapshots() != null )
    {
      super.setSnapshots( repository.getSnapshots().clone() );
    }
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder( 256 );
    sb.append( "       id: " ).append( getId() ).append( "\n" );
    sb.append( "      url: " ).append( getUrl() ).append( "\n" );
    sb.append( "   layout: " ).append( getLayout() ).append( "\n" );
    if ( getReleases() != null )
    {
      sb.append( "   releas: " ).append( getReleases().isEnabled() )
          .append( " " )
          .append( getReleases().getUpdatePolicy() )
          .append( "\n" );
    }
    else
    {
      sb.append( "   releas: " ).append( getReleases() ).append( "\n" );
    }
    if ( getSnapshots() != null )
    {
      sb.append( "   snapsh: " ).append( getSnapshots().isEnabled() )
          .append( " " )
          .append( getSnapshots().getUpdatePolicy() ) ;
    }
    else
    {
      sb.append( "   snapsh: " ).append( getSnapshots() );
    }

//    RepositoryPolicy snapshotPolicy = remoteRepository.getPolicy( true );
//    sb.append( "snapshots: [enabled => " ).append( snapshotPolicy.isEnabled() );
//    sb.append( ", update => " ).append( snapshotPolicy.getUpdatePolicy() ).append( "]\n" );
//
//    RepositoryPolicy releasePolicy = remoteRepository.getPolicy( false );
//    sb.append( " releases: [enabled => " ).append( releasePolicy.isEnabled() );
//    sb.append( ", update => " ).append( releasePolicy.getUpdatePolicy() ).append( "]\n" );

    return sb.toString();
  }

  @Override
  public int hashCode()
  {
    return Objects.hash( getId(), getUrl() );
  }

  @Override
  public boolean equals( Object obj )
  {
    if ( this == obj )
    {
      return true;
    }
    if ( obj == null || getClass() != obj.getClass() )
    {
      return false;
    }
    final CollectedRepository other = (CollectedRepository) obj;
    return Objects.equals( this.getId(), other.getId() )
        && Objects.equals( this.getUrl(), other.getUrl() );
  }
}
