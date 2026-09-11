import { MissingPerson } from './report';
import { Sighting, PersonTimeline } from './sighting';
import { NotificationListResponse } from './notification';
import { UserProfile } from './auth';

export type UserResponse = UserProfile;
export type MissingPersonResponse = MissingPerson;
export type SightingResponse = Sighting;
export type { PersonTimeline, NotificationListResponse };

export interface MissingPersonListResponse {
  items: MissingPerson[];
  total: number;
  page: number;
  per_page: number;
}
