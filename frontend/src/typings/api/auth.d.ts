declare namespace Api {
  /**
   * namespace Auth
   *
   * backend api module: "auth"
   */
  namespace Auth {
    interface LoginResponse {
      authenticated?: boolean;
      session_seconds?: number;
    }

    interface UserInfo {
      authenticated?: boolean;
      userId: string;
      userName: string;
      roles: string[];
      buttons: string[];
    }
  }
}
