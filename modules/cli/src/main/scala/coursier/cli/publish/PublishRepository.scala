package coursier.cli.publish

import coursier.core.Authentication
import coursier.maven.MavenRepository

sealed abstract class PublishRepository extends Product with Serializable {
  def snapshotRepo: MavenRepository
  def releaseRepo: MavenRepository
  def readSnapshotRepo: MavenRepository
  def readReleaseRepo: MavenRepository

  final def repo(isSnapshot: Boolean): MavenRepository =
    if (isSnapshot)
      snapshotRepo
    else
      releaseRepo
  final def readRepo(isSnapshot: Boolean): MavenRepository =
    if (isSnapshot)
      readSnapshotRepo
    else
      readReleaseRepo
  def checkResultsRepo(isSnapshot: Boolean): MavenRepository =
    readRepo(isSnapshot)

  def withAuthentication(auth: Authentication): PublishRepository
}

object PublishRepository {

  final case class Simple(
    snapshotRepo: MavenRepository,
    readRepoOpt: Option[MavenRepository] = None
  ) extends PublishRepository {
    def releaseRepo: MavenRepository = snapshotRepo
    def readSnapshotRepo: MavenRepository = readRepoOpt.getOrElse(snapshotRepo)
    def readReleaseRepo: MavenRepository = readSnapshotRepo

    def withAuthentication(auth: Authentication): Simple =
      copy(
        snapshotRepo = snapshotRepo.copy(
          authentication = Some(auth)
        ),
        readRepoOpt = readRepoOpt
          .map(_.copy(authentication = Some(auth)))
      )
  }

  final case class Bintray(
    user: String,
    repository: String,
    package0: String,
    apiKey: String,
    overrideAuthOpt: Option[Authentication]
  ) extends PublishRepository {

    def authentication: Authentication =
      overrideAuthOpt.getOrElse(Authentication(user, apiKey))

    def releaseRepo: MavenRepository =
      MavenRepository(
        s"https://api.bintray.com/maven/$user/$repository/$package0",
        authentication = Some(authentication)
      )
    def snapshotRepo: MavenRepository =
      releaseRepo

    def readReleaseRepo: MavenRepository =
      MavenRepository(s"https://dl.bintray.com/$user/$repository")
    def readSnapshotRepo: MavenRepository =
      readReleaseRepo

    def withAuthentication(auth: Authentication): Bintray =
      copy(overrideAuthOpt = Some(auth))

  }

  final case class GitHub(
    username: String,
    token: String,
    overrideAuthOpt: Option[Authentication]
  ) extends PublishRepository {

    def releaseRepo: MavenRepository =
      MavenRepository(
        s"https://maven.pkg.github.com/$username",
        authentication = overrideAuthOpt.orElse(Some(Authentication(username, token)))
      )
    def snapshotRepo: MavenRepository =
      releaseRepo

    def readReleaseRepo: MavenRepository =
      releaseRepo
    def readSnapshotRepo: MavenRepository =
      releaseRepo

    def withAuthentication(auth: Authentication): GitHub =
      copy(overrideAuthOpt = Some(auth))

  }

  final case class Sonatype(base: MavenRepository) extends PublishRepository {

    def snapshotRepo: MavenRepository =
      base.copy(
        root = s"${base.root}/content/repositories/snapshots"
      )
    def releaseRepo: MavenRepository =
      base.copy(
        root = s"$restBase/staging/deploy/maven2"
      )
    def releaseRepoOf(repoId: String): MavenRepository =
      base.copy(
        root = s"$restBase/staging/deployByRepositoryId/$repoId"
      )
    def readSnapshotRepo: MavenRepository =
      snapshotRepo
    def readReleaseRepo: MavenRepository =
      base.copy(
        root = s"${base.root}/content/repositories/releases"
      )

    override def checkResultsRepo(isSnapshot: Boolean): MavenRepository =
      if (isSnapshot)
        super.checkResultsRepo(isSnapshot)
      else
        base.copy(
          root = s"${base.root}/content/repositories/public"
        )

    def restBase: String =
      s"${base.root}/service/local"

    def withAuthentication(auth: Authentication): Sonatype =
      copy(
        base = base.copy(
          authentication = Some(auth)
        )
      )
  }

  def gitHub(username: String, token: String): PublishRepository =
    GitHub(username, token, None)

  def bintray(user: String, repository: String, package0: String, apiKey: String): PublishRepository =
    Bintray(user, repository, package0, apiKey, None)

}
