/** The storage namespace */
declare namespace StorageType {
  interface Session {
  }

  interface Local {
    /** The i18n language */
    lang: App.I18n.LangType;
    /** The token */
    token: string;
    /** The last login user id */
    lastLoginUserId: string;
  }
}
