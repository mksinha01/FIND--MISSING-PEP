import pytest
from sqlalchemy import MetaData
from sqlalchemy.schema import CreateTable
from sqlalchemy.dialects import postgresql

from app.models.base import Base
from app.models.user import User
from app.models.missing_person import MissingPerson
from app.models.photo import Photo
from app.models.face_embedding import FaceEmbedding
from app.models.edge_agent import EdgeAgent
from app.models.camera import Camera
from app.models.sighting import Sighting
from app.models.notification import Notification
from app.models.audit_log import AuditLog

def test_sighting_person_id_cascade():
    """Verify that sighting.person_id has ondelete='CASCADE'"""
    sighting_table = Sighting.__table__
    
    person_fk = next(
        (fk for fk in sighting_table.foreign_keys if fk.column.name == 'id' and fk.column.table.name == 'missing_persons'), 
        None
    )
    assert person_fk is not None, "Foreign key to missing_persons not found"
    assert person_fk.ondelete == 'CASCADE', "sighting.person_id foreign key must have ondelete='CASCADE'"

def test_face_embedding_columns():
    """Verify that face_embedding has updated_at and is_active columns"""
    embedding_table = FaceEmbedding.__table__
    
    assert 'updated_at' in embedding_table.columns, "FaceEmbedding must have updated_at column"
    assert 'is_active' in embedding_table.columns, "FaceEmbedding must have is_active column"

def test_camera_columns_and_constraints():
    """Verify that camera has encrypted_rtsp_url and composite unique constraint"""
    camera_table = Camera.__table__
    
    assert 'encrypted_rtsp_url' in camera_table.columns, "Camera must have encrypted_rtsp_url column"
    assert 'local_camera_id' in camera_table.columns, "Camera must have local_camera_id column"
    
    unique_constraints = [c for c in camera_table.constraints if type(c).__name__ == 'UniqueConstraint']
    has_composite = any(
        set(col.name for col in uc.columns) == {'agent_id', 'local_camera_id'} 
        for uc in unique_constraints
    )
    assert has_composite, "Camera must have a composite unique constraint on (agent_id, local_camera_id)"

def test_sqlalchemy_2_0_annotations():
    """Verify that models use SQLAlchemy 2.0 type hints."""
    import typing
    from sqlalchemy.orm import Mapped
    
    # Just a simple inspection of the annotations dictionary
    sighting_annotations = typing.get_type_hints(Sighting)
    assert 'similarity_score' in sighting_annotations
    
    # Python 3.9+ typing origin inspection
    origin = typing.get_origin(sighting_annotations['similarity_score'])
    # In some python versions/typing setups, origin might be Mapped directly
    # Check if the name Mapped is in the string representation if direct comparison fails
    assert 'Mapped' in str(sighting_annotations['similarity_score']), "Must use SQLAlchemy 2.0 Mapped annotations"
