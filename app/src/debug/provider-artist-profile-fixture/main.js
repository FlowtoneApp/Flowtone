const keyword = 'profiletest';
const avatar = (label, color) =>
  `https://placehold.co/256x256/${color}/FFFFFF.png?text=${encodeURIComponent(label)}`;

const artists = [
  {
    id: 'profile-basic',
    title: 'Profile Test · Basic',
    artworkUrl: avatar('Basic', '0F766E')
  },
  {
    id: 'profile-counts',
    title: 'Profile Test · Counts',
    songCount: 12,
    albumCount: 3
  },
  {
    id: 'profile-text',
    title: 'Profile Test · Text',
    aliases: ['Test Alias', '测试别名'],
    biography: '这是一段用于验证 Provider Artist Profile 文本布局的测试简介。它应该在常见手机屏幕上覆盖多行，并保持别名、简介与统计信息的独立显示语义。'
  },
  {
    id: 'profile-full',
    title: 'Profile Test · Full',
    artworkUrl: avatar('Full', 'B45309'),
    aliases: ['Full Alias', '完整资料'],
    biography: '完整资料 fixture 用于同时验证头像、别名、简介与 Provider 统计信息。它不提供歌曲或专辑实体集合。',
    songCount: 27,
    albumCount: 5
  },
  {
    id: 'profile-zero',
    title: 'Profile Test · Zero',
    songCount: 0,
    albumCount: 0
  },
  {
    id: 'profile-invalid',
    title: 'Profile Test · Invalid',
    songCount: -1,
    albumCount: -10
  },
  {
    id: 'profile-partial',
    title: 'Profile Test · Partial',
    artworkUrl: avatar('Partial', '7C3AED'),
    songCount: 20,
    albumCount: 4
  }
];

globalThis.flowtoneExtension = {
  async searchPage(request) {
    if (
      request.keyword !== keyword ||
      request.category !== 'user' ||
      request.cursor !== null
    ) {
      return { results: [], nextCursor: null };
    }

    return {
      results: artists.map((artist) => ({
        ...artist,
        artist: 'Provider Artist Profile Fixture',
        category: 'user'
      })),
      nextCursor: null
    };
  },

  async findArtistMetadata(request) {
    if (request.artistName !== 'Profile Test · Partial') {
      return { type: 'not_found' };
    }
    return {
      type: 'found',
      aliases: ['Registry Alias'],
      biography: 'This biography comes from metadata resolver.'
    };
  }
};
