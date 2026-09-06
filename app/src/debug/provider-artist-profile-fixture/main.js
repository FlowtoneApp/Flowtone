const COLLECTION_DELAY_MS = 3000;

const image = (width, height, color, label) =>
  `https://placehold.co/${width}x${height}/${color}/FFFFFF.png?text=${encodeURIComponent(label)}`;

const artists = [
  {
    id: 'banner-scroll',
    title: 'Banner Scroll Test',
    artworkUrl: image(256, 256, '0F766E', 'Banner'),
    bannerUrl: image(1600, 620, '0F766E', 'Banner+Scroll'),
    aliases: ['Docked Horizon', 'Banner Fixture'],
    biography: 'A deterministic medium-length biography for observing the expanded banner header, its collapsed content handoff, and its docked identity. It intentionally occupies more than two compact lines without becoming long enough to require focused internal scrolling.',
    songCount: 25,
    albumCount: 4
  },
  {
    id: 'no-banner-cloud',
    title: 'No Banner Cloud Test',
    artworkUrl: image(256, 256, '4338CA', 'Cloud'),
    aliases: ['Cloud Fixture', 'No Hero Image'],
    biography: 'This artist intentionally has no banner URL.\nThe cloud must remain visible behind the expanded identity and Biography Focus presentation.\nThis third short line guarantees Focus without turning the fixture into an internal-scroll test.',
    songCount: 5,
    albumCount: 1
  },
  {
    id: 'long-artist-title',
    title: 'This Is An Extremely Long Artist Name Used To Verify Top Bar Ellipsis Behaviour',
    artworkUrl: image(256, 256, '9A3412', 'Long+Name'),
    aliases: ['Long Title Fixture'],
    biography: 'A compact profile used to verify that a docked title yields its avatar before it ellipsizes and exposes the full-title overlay.',
    songCount: 18,
    albumCount: 1
  },
  {
    id: 'long-biography',
    title: 'Long Biography Test',
    artworkUrl: image(256, 256, '7C3AED', 'Biography'),
    aliases: ['Focus Fixture', 'Scrollable Biography'],
    biography: 'This original fixture biography is deliberately long. It begins with a calm description of a fictional recording project built for visual regression work. Each paragraph adds enough distinct material to exceed the focused profile viewport on a phone-sized display. The compact state should reveal only the intended short excerpt before the focused presentation expands.\n\nIn the focused state, this text must remain readable while the surrounding Artist page stays stable. The internal biography viewport should become scrollable without changing the provider identity, Artist counts, Album associations, or page navigation entry. The words are intentionally ordinary and self-contained so the fixture does not depend on copyrighted artist notes or remote metadata.\n\nA final paragraph provides additional vertical length for reliable regression coverage. It mentions slow afternoons, synthetic clouds, carefully labelled tracks, and a test bench where every title, identifier, and ordering value remains fixed between builds. The purpose is not musical realism; it is a predictable focused biography that can be expanded, scrolled, dismissed, and revisited during UI verification.',
    songCount: 4,
    albumCount: 1
  },
  {
    id: 'exactly-two-line-bio',
    title: 'Exactly Two Line Biography Test',
    artworkUrl: image(256, 256, '0369A1', 'Two+Lines'),
    aliases: ['No Reveal Fixture'],
    biography: 'First clear fixture line.\nSecond clear fixture line.',
    songCount: 3,
    albumCount: 1
  },
  {
    id: 'slow-loading-banner',
    title: 'Slow Loading / 7 Skeleton Test',
    artworkUrl: image(256, 256, 'BE123C', 'Slow'),
    bannerUrl: image(1600, 620, 'BE123C', 'Slow+Loading'),
    aliases: ['Async Fixture'],
    biography: 'This banner profile is available immediately while the shared Provider song and album collections wait for the deterministic fixture delay.',
    songCount: 4,
    albumCount: 1
  }
];

const albums = [
  album('banner-normal-album', 'Banner Normal Album', 'banner-scroll', 4),
  album(
    'long-album-title',
    'An Extremely Long Album Title Created Specifically To Verify Secondary Top Bar Ellipsis Behaviour',
    'banner-scroll',
    3
  ),
  album('banner-many-tracks', 'Banner Many Tracks', 'banner-scroll', 15),
  album('banner-night-album', 'Night Signals', 'banner-scroll', 3),
  album('cloud-archive', 'Cloud Archive', 'no-banner-cloud', 5),
  album('long-title-record', 'A Reasonably Titled Record', 'long-artist-title', 18),
  album('biography-notes', 'Biography Notes', 'long-biography', 4),
  album('two-line-notes', 'Two Line Notes', 'exactly-two-line-bio', 3),
  album('slow-arrival', 'Slow Arrival', 'slow-loading-banner', 4)
];

const songs = [];
addTracks('banner-scroll', 'banner-normal-album', 4, 'Banner Normal Track');
addTracks(
  'banner-scroll',
  'long-album-title',
  3,
  'Long Album Track',
  'A Very Long Song Title Included To Verify That Artist Song Rows Keep Their Existing Presentation Geometry'
);
addTracks('banner-scroll', 'banner-many-tracks', 15, 'Many Tracks');
addTracks('banner-scroll', 'banner-night-album', 3, 'Night Signal');
addTracks('no-banner-cloud', 'cloud-archive', 5, 'Cloud Archive Track');
addTracks('long-artist-title', 'long-title-record', 18, 'Long Artist Track');
addTracks('long-biography', 'biography-notes', 4, 'Biography Note');
addTracks('exactly-two-line-bio', 'two-line-notes', 3, 'Two Line Note');
addTracks('slow-loading-banner', 'slow-arrival', 4, 'Slow Arrival Track');

let collectionDelayDeadline = 0;

function artistById(id) {
  return artists.find((artist) => artist.id === id);
}

function album(id, title, artistId, songCount) {
  const artist = artistById(artistId);
  return {
    id,
    title,
    artist: artist.title,
    artists: [{ id: artist.id, name: artist.title }],
    artworkUrl: image(600, 600, artistId === 'banner-scroll' ? '115E59' : '475569', title),
    songCount,
    releaseMetadata: 'UI regression fixture'
  };
}

function addTracks(artistId, albumId, count, prefix, longTitle) {
  const artist = artistById(artistId);
  const albumEntity = albums.find((albumItem) => albumItem.id === albumId);
  for (let index = 1; index <= count; index += 1) {
    const isMultiArtist = artistId === 'banner-scroll' && albumId === 'banner-many-tracks' && index === 2;
    songs.push({
      id: `${albumId}-track-${index}`,
      title: longTitle && index === 1 ? longTitle : `${prefix} ${index}`,
      artist: isMultiArtist ? `${artist.title} / Guest Fixture` : artist.title,
      artists: isMultiArtist
        ? [
            { id: artist.id, name: artist.title },
            { id: 'guest-fixture', name: 'Guest Fixture' }
          ]
        : [{ id: artist.id, name: artist.title }],
      durationMs: 168000 + index * 7000,
      ...(index % 3 === 0 ? {} : {
        artworkUrl: image(600, 600, index % 2 === 0 ? '1D4ED8' : '0F766E', `${prefix}+${index}`)
      }),
      album: { id: albumEntity.id, title: albumEntity.title }
    });
  }
}

function waitForFirstCollectionRead() {
  if (collectionDelayDeadline === 0) {
    collectionDelayDeadline = Date.now() + COLLECTION_DELAY_MS;
  }
  while (Date.now() < collectionDelayDeadline) {
    // Debug-only isolate delay: collection APIs have no Artist request parameter or timer bridge.
  }
}

function artistSearchResult(artist) {
  return {
    id: artist.id,
    title: artist.title,
    artist: 'Flowtone UI Test',
    category: 'user',
    artworkUrl: artist.artworkUrl,
    largeArtworkUrl: artist.artworkUrl,
    ...(artist.bannerUrl ? { bannerUrl: artist.bannerUrl } : {}),
    aliases: artist.aliases,
    biography: artist.biography,
    songCount: artist.songCount,
    albumCount: artist.albumCount
  };
}

globalThis.flowtoneExtension = {
  async searchPage(request) {
    if (request.category !== 'user' || request.cursor !== null) {
      return { results: [], nextCursor: null };
    }
    const keyword = String(request.keyword || '').trim().toLowerCase();
    const results = artists
      .filter((artist) => artist.id.includes(keyword) || artist.title.toLowerCase().includes(keyword))
      .slice(0, request.limit)
      .map(artistSearchResult);
    return { results, nextCursor: null };
  },

  async getSongs() {
    waitForFirstCollectionRead();
    return songs;
  },

  async getAlbums() {
    waitForFirstCollectionRead();
    return albums;
  },

  async findArtistMetadata(request) {
    const artist = artists.find((item) => item.title === request.artistName);
    if (!artist) return { type: 'not_found' };
    return {
      type: 'found',
      aliases: artist.aliases,
      biography: artist.biography,
      songCount: artist.songCount,
      albumCount: artist.albumCount
    };
  },

  async resolvePersistentSong(request) {
    return songs.find((song) => song.persistentId === request.persistentId) || {};
  },

  async getPlaybackResource() {
    // Presentation-only fixture: no remote audio is introduced for UI regression coverage.
    return { type: 'unsupported' };
  }
};
